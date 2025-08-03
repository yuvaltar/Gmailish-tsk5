// server/controllers/mailsController.js

const mailsService = require('../services/mail');
const Mail         = require('../models/mail');
const { sendToCpp } = require('../services/blacklistService');

const URL_REGEX = /(?:(?:file:\/\/(?:[A-Za-z]:)?(?:\/[^s]*)?)|(?:[A-Za-z][A-Za-z0-9+\-\.]*:\/\/)?(?:localhost|(?:[A-Za-z0-9\-]+\.)+[A-Za-z0-9\-]+|(?:\d{1,3}\.){3}\d{1,3})(?::\d+)?(?:\/[^s]*)?)/g;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Save a draft.
 */
exports.saveDraft = async (req, res) => {
  try {
    const { to, subject, content } = req.body;
    if (!subject && !content && !to) {
      return res.status(400).json({ error: 'Draft is empty' });
    }
    // 'to' must be a valid userId
    const draft = await mailsService.saveDraft(
      req.user.id,
      to,
      subject?.trim() || '',
      content?.trim() || ''
    );
    return res.status(201).json(draft);
  } catch (err) {
    if (err.message === 'Sender not found' || err.message === 'Recipient not found') {
      return res.status(400).json({ error: err.message });
    }
    console.error('saveDraft error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Send a new mail (inbox + sent copies).
 * Applies blacklist check before creation.
 */
async function containsBlacklistedUrl(text) {
  const matches = Array.from(text.matchAll(URL_REGEX), m => m[0]);
  for (const url of matches) {
    const result = await sendToCpp(`GET ${url}`);
    if (result.startsWith('200 Ok')) {
      const flags = result.split('\n').slice(1).join(' ').trim();
      if (flags === 'true true') {
        return { blacklisted: true, url };
      }
    } else if (result.startsWith('404 Not Found')) {
      continue;
    } else {
      return { error: true, url };
    }
  }
  return { blacklisted: false };
}

exports.sendMail = async (req, res) => {
  try {
    const { to, subject, content } = req.body;
    if (!to || !subject || !content) {
      return res.status(400).json({ error: 'Missing fields' });
    }

    // Check URLs against blacklist
    const check = await containsBlacklistedUrl(`${subject} ${content}`);
    if (check.error) {
      return res.status(500).json({ error: `Blacklist error for ${check.url}` });
    }
    const recipientLabels = check.blacklisted ? ['spam'] : ['inbox'];

    const { sentMail } = await mailsService.createMail(
      req.user.id,
      to,
      subject.trim(),
      content.trim(),
      recipientLabels
    );
    return res.status(201).json(sentMail);
  } catch (err) {
    if (err.message === 'Sender not found' || err.message === 'Recipient not found') {
      return res.status(400).json({ error: err.message });
    }
    console.error('sendMail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Get inbox (or by custom label) for the authenticated user.
 */
exports.getInbox = async (req, res) => {
  try {
    const userId = req.user.id;
    const { label } = req.query;

    let list;
    if (label) {
      list = await mailsService.getEmailsByLabelName(label, userId);
    } else {
      list = await mailsService.getInboxForUser(userId);
    }

    return res.status(200).json(list);
  } catch (err) {
    console.error('getInbox error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Get all spam mails for the authenticated user.
 */
exports.getSpam = async (req, res) => {
  try {
    const spamList = await mailsService.getEmailsByLabelName('spam', req.user.id);
    return res.status(200).json(spamList);
  } catch (err) {
    console.error('getSpam error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Toggle the spam label and update the external blacklist.
 */
exports.toggleSpam = async (req, res) => {
  try {
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }

    const text = `${mail.subject} ${mail.content}`;
    const urls = Array.from(text.matchAll(URL_REGEX), m => m[0]);

    if (mail.labels.includes('spam')) {
      // unmark spam
      mail.labels = mail.labels.filter(l => l !== 'spam');
      for (const url of urls) {
        await sendToCpp(`DELETE ${url}`);
        await delay(50);
      }
      await mail.save();
      return res.status(200).json({ message: 'Unmarked as spam', mail: mail.toObject() });
    } else {
      // mark spam
      mail.labels.push('spam');
      for (const url of urls) {
        const result = (await sendToCpp(`POST ${url}`)).trim();
        if (!['201 Created', '409 Conflict'].includes(result)) {
          console.error('Blacklist error on:', url, '→', result);
          return res.status(500).json({ error: `Failed to blacklist ${url}: ${result}` });
        }
        await delay(50);
      }
      await mail.save();
      return res.status(200).json({ message: 'Marked as spam', mail: mail.toObject() });
    }
  } catch (err) {
    console.error('toggleSpam error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Fetch a single mail by ID for the authenticated user.
 */
exports.getMailById = async (req, res) => {
  try {
    const mail = await mailsService.getMailById(req.params.id);
    if (mail.ownerId !== req.user.id) {
      return res.status(404).json({ error: 'Mail not found or not accessible' });
    }
    return res.status(200).json(mail);
  } catch (err) {
    console.error('getMailById error:', err);
    return res.status(404).json({ error: err.message });
  }
};

/**
 * Update subject/content of an existing mail.
 */
exports.updateMail = async (req, res) => {
  try {
    const { subject, content } = req.body;
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }
    if (subject) mail.subject = subject.trim();
    if (content) mail.content = content.trim();
    await mail.save();
    return res.status(204).end();
  } catch (err) {
    console.error('updateMail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Delete a single mail by ID.
 */
exports.deleteMail = async (req, res) => {
  try {
    const deletedCount = await mailsService.deleteMailById(req.params.id);
    if (!deletedCount) {
      return res.status(404).json({ error: 'Mail not found' });
    }
    return res.status(204).end();
  } catch (err) {
    console.error('deleteMail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Permanently clear trash for the authenticated user.
 */
exports.clearTrash = async (req, res) => {
  try {
    const count = await mailsService.clearTrash(req.user.id);
    return res.status(200).json({ message: `Deleted ${count} emails from trash.` });
  } catch (err) {
    console.error('clearTrash error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Search mails by text across subject/content.
 */
exports.searchMails = async (req, res) => {
  try {
    const results = await mailsService.searchMails(req.user.id, req.params.query);
    return res.status(200).json(results);
  } catch (err) {
    console.error('searchMails error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Search mails within a specific label.
 */
exports.searchMailsByLabel = async (req, res) => {
  try {
    const { label, query } = req.params;
    if (!label || !query) {
      return res.status(400).json({ error: 'Missing label or query' });
    }
    const results = await mailsService.searchMailsWithLabel(req.user.id, query, label);
    return res.status(200).json(results);
  } catch (err) {
    console.error('searchMailsByLabel error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Add a custom label to a mail.
 */
exports.addLabelToEmail = async (req, res) => {
  try {
    const { label } = req.body;
    if (!label || typeof label !== 'string' || !label.trim()) {
      return res.status(400).json({ error: 'Label must be a non-empty string' });
    }
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }
    if (!mail.labels.includes(label)) mail.labels.push(label);
    await mail.save();
    return res.status(200).json({ message: `Label '${label}' added`, mail: mail.toObject() });
  } catch (err) {
    console.error('addLabelToEmail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Remove a label from a mail.
 */
exports.removeLabelFromEmail = async (req, res) => {
  try {
    const { label } = req.params;
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }
    mail.labels = mail.labels.filter(l => l !== label);
    await mail.save();
    return res.status(200).json({ message: `Label '${label}' removed`, mail: mail.toObject() });
  } catch (err) {
    console.error('removeLabelFromEmail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Toggle the "read" flag on a mail.
 */
exports.markAsRead = async (req, res) => {
  try {
    await mailsService.markAsReadById(req.params.id, req.user.id);
    const mail = await mailsService.getMailById(req.params.id);
    return res.status(200).json(mail);
  } catch (err) {
    console.error('markAsRead error:', err);
    return res.status(404).json({ error: err.message });
  }
};

/**
 * Mark multiple mails as unread.
 */
exports.markAsUnread = async (req, res) => {
  try {
    const { ids } = req.body;
    if (!Array.isArray(ids)) {
      return res.status(400).json({ error: 'Expected array of IDs' });
    }
    await mailsService.markAsUnreadByIds(ids, req.user.id);
    return res.sendStatus(204);
  } catch (err) {
    console.error('markAsUnread error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Mark all mails as read.
 */
exports.markAllAsRead = async (req, res) => {
  try {
    await mailsService.markAllAsRead(req.user.id);
    return res.sendStatus(204);
  } catch (err) {
    console.error('markAllAsRead error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Toggle the "star" label on a mail.
 */
exports.toggleStar = async (req, res) => {
  try {
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }

    if (mail.labels.includes('starred')) {
      mail.labels = mail.labels.filter(label => label !== 'starred');
    } else {
      mail.labels.push('starred');
    }

    await mail.save();
    return res.status(200).json({ message: 'Star toggled', mail: mail.toObject() });
  } catch (err) {
    console.error('toggleStar error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};