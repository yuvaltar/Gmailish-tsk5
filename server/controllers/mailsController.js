// server/controllers/mailsController.js

const mailsService = require('../services/mail');
const Mail         = require('../models/mail');
const {
  isBlacklisted,
  addUrl,
  removeUrl,
} = require('../services/blacklistService');

// Simple, robust URL regex that also matches "www.example.com"
const URL_REGEX = /\b(https?:\/\/[^\s"'<>()]+|www\.[^\s"'<>()]+)\b/gi;

// Labels we consider “inbox-like” and must be dropped when spam is applied
const INBOX_LIKE = new Set([
  'primary', 'inbox', 'promotions', 'social', 'updates',
  'important', 'archive', 'sent', 'drafts'
]);

// --- helpers ---------------------------------------------------------------

// Extract and normalize URLs from arbitrary text
function extractUrls(text) {
  if (!text || typeof text !== 'string') return [];
  const found = text.match(URL_REGEX) || [];

  // Normalize & trim common trailing punctuation
  const cleaned = found
    .map(u => u.replace(/[),.;!?]+$/, ''))        // strip trailing , . ; ! ?
    .map(u => (u.startsWith('www.') ? `http://${u}` : u)); // normalize bare www.

  // De-duplicate
  return [...new Set(cleaned)];
}

// Check if any extracted URL is currently blacklisted
async function containsBlacklistedUrl(text) {
  const urls = extractUrls(text);
  for (const url of urls) {
    if (await isBlacklisted(url)) {
      return { blacklisted: true, url };
    }
  }
  return { blacklisted: false };
}

// --------------------------------------------------------------------------

/**
 * Save a draft.
 */
exports.saveDraft = async (req, res) => {
  try {
    const { to, subject, content } = req.body;
    if (!subject && !content && !to) {
      return res.status(400).json({ error: 'Draft is empty' });
    }
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
 * Send (create) a mail.
 * If any URL in subject/content is already blacklisted → create recipient mail as spam.
 */
exports.sendMail = async (req, res) => {
  try {
    const { to, subject, content } = req.body;
    if (!to || !subject || !content) {
      return res.status(400).json({ error: 'Missing fields' });
    }

    const check = await containsBlacklistedUrl(`${subject} ${content}`);
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
    const list = label
      ? await mailsService.getEmailsByLabelName(label, userId)
      : await mailsService.getInboxForUser(userId);
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
 * Toggle spam (legacy endpoint).
 * NOTE: This will add URLs to blacklist when marking spam,
 * and remove them when unmarking spam.
 */
exports.toggleSpam = async (req, res) => {
  try {
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }

    const urls = extractUrls(`${mail.subject} ${mail.content}`);

    if (mail.labels.includes('spam')) {
      // Unmark spam → remove URLs from blacklist
      mail.labels = mail.labels.filter(l => l !== 'spam');
      for (const url of urls) {
        try { await removeUrl(url); } catch (_) { /* ignore */ }
      }
    } else {
      // Mark spam → add URLs to blacklist
      mail.labels.push('spam');
      for (const url of urls) {
        try { await addUrl(url); } catch (_) { /* ignore duplicates */ }
      }
      // keep only spam (+ allow starred)
      mail.labels = mail.labels.filter(l =>
        l.toLowerCase() === 'spam' || l.toLowerCase() === 'starred' || !INBOX_LIKE.has(l.toLowerCase())
      );
    }

    await mail.save();
    const message = mail.labels.includes('spam') ? 'Marked as spam' : 'Unmarked as spam';
    return res.status(200).json({ message, mail: mail.toObject() });
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
 * Add a custom label to a mail (with optional 'add' or 'remove' action).
 * Normalize "inbox" → "primary".
 * When ADDING 'spam':
 *   1) extract all URLs from subject+content and add them to blacklist
 *   2) keep ONLY spam (and let 'starred' coexist)
 */
exports.addLabelToEmail = async (req, res) => {
  try {
    let { label, action } = req.body;
    const resolvedAction = action || 'add';

    if (!label || typeof label !== 'string' || !label.trim()) {
      return res.status(400).json({ error: 'Label must be a non-empty string' });
    }

    label = label.trim();
    if (label.toLowerCase() === 'inbox') label = 'primary';

    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }

    if (resolvedAction === 'add') {
      if (!mail.labels.includes(label)) {
        mail.labels.push(label);
      }

      if (label.toLowerCase() === 'spam') {
        const urls = extractUrls(`${mail.subject} ${mail.content}`);
        for (const u of urls) {
          try { await addUrl(u); } catch (_) { /* duplicate/ignore */ }
        }
        // keep only spam (+ starred if present)
        mail.labels = mail.labels.filter(l =>
          l.toLowerCase() === 'spam' || l.toLowerCase() === 'starred' || !INBOX_LIKE.has(l.toLowerCase())
        );
      }

    } else if (resolvedAction === 'remove') {
      mail.labels = mail.labels.filter(l => l !== label);
      // Intentionally NOT removing URLs from blacklist here.
    } else {
      return res.status(400).json({ error: `Invalid action '${resolvedAction}'` });
    }

    await mail.save();
    return res.status(200).json({
      message: `Label '${label}' ${resolvedAction}ed`,
      mail: mail.toObject(),
    });
  } catch (err) {
    console.error('addLabelToEmail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Remove a label from a mail (by path param).
 */
exports.removeLabelFromEmail = async (req, res) => {
  try {
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }
    mail.labels = mail.labels.filter(l => l !== req.params.label);
    await mail.save();
    return res.status(200).json({
      message: `Label '${req.params.label}' removed`,
      mail: mail.toObject()
    });
  } catch (err) {
    console.error('removeLabelFromEmail error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

/**
 * Toggle "read".
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
 * Mark multiple as unread.
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
 * Mark all as read.
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
 * Toggle star.
 */
exports.toggleStar = async (req, res) => {
  try {
    const mail = await Mail.findOne({ id: req.params.id, ownerId: req.user.id });
    if (!mail) {
      return res.status(404).json({ error: 'Mail not found or not owned by you' });
    }

    if (mail.labels.includes('starred')) {
      mail.labels = mail.labels.filter(l => l !== 'starred');
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
