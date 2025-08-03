// server/services/mails.js

const Mail   = require('../models/mail');
const User   = require('../models/user');
const uuidv4 = require('../utils/uuid');

/**
 * Create both an inbox copy and a sent copy of a mail.
 * Throws if sender or recipient cannot be found.
 */
async function createMail(senderId, recipientId, subject, content, recipientLabels = ['inbox']) {
  // Validate sender
  const sender = await User.findOne({ id: senderId }).lean();
  if (!sender) {
    throw new Error('Sender not found');
  }

  // Validate recipient
  const recipient = await User.findOne({ id: recipientId }).lean();
  if (!recipient) {
    throw new Error('Recipient not found');
  }

  const timestamp = new Date();

  const inboxMail = {
    id:             uuidv4(),
    senderId,
    senderName:     `${sender.firstName} ${sender.lastName}`,
    recipientId,
    recipientName:  `${recipient.firstName} ${recipient.lastName}`,
    recipientEmail: recipient.email,
    subject,
    content,
    timestamp,
    labels:         recipientLabels,
    ownerId:        recipientId,
    read:           false
  };

  const sentMail = {
    ...inboxMail,
    id:      uuidv4(),
    ownerId: senderId,
    labels:  ['sent'],
    read:    true
  };

  const [inboxDoc, sentDoc] = await Mail.insertMany([inboxMail, sentMail]);
  return {
    inboxMail: inboxDoc.toObject(),
    sentMail:  sentDoc.toObject()
  };
}

/**
 * Save a draft mail.
 * Requires a valid sender and recipient.
 */
async function saveDraft(senderId, recipientId, subject, content) {
  // Validate sender
  const sender = await User.findOne({ id: senderId }).lean();
  if (!sender) {
    throw new Error('Sender not found');
  }

  // Validate recipient
  const recipient = await User.findOne({ id: recipientId }).lean();
  if (!recipient) {
    throw new Error('Recipient not found');
  }

  const timestamp = new Date();

  const draft = {
    id:             uuidv4(),
    senderId,
    senderName:     `${sender.firstName} ${sender.lastName}`,
    recipientId,
    recipientName:  `${recipient.firstName} ${recipient.lastName}`,
    recipientEmail: recipient.email,
    subject,
    content,
    timestamp,
    labels:         ['drafts'],
    ownerId:        senderId,
    read:           false
  };

  const doc = await Mail.create(draft);
  return doc.toObject();
}

/**
 * Fetch a single mail by its UUID.
 * Throws if not found.
 */
async function getMailById(id) {
  const mail = await Mail.findOne({ id }).lean();
  if (!mail) {
    throw new Error('Mail not found');
  }
  return mail;
}

/**
 * Delete one mail by its UUID.
 * Returns the number of documents deleted (0 or 1).
 */
async function deleteMailById(id) {
  const result = await Mail.deleteOne({ id });
  return result.deletedCount;
}

/**
 * Permanently clear all trashed mails for a given user.
 * Returns the number of mails deleted.
 */
async function clearTrash(userId) {
  const result = await Mail.deleteMany({
    ownerId: userId,
    labels:  { $all: ['trash'] }
  });
  return result.deletedCount;
}

/**
 * Mark a single mail as read for a given user.
 */
async function markAsReadById(id, userId) {
  await Mail.updateOne(
    { id, ownerId: userId },
    { $set: { read: true }, $addToSet: { labels: 'read' } }
  );
}

/**
 * Mark multiple mails as unread for a given user.
 */
async function markAsUnreadByIds(ids, userId) {
  await Mail.updateMany(
    { id: { $in: ids }, ownerId: userId },
    { $set: { read: false }, $pull: { labels: 'read' } }
  );
}

/**
 * Mark all mails as read for a given user.
 */
async function markAllAsRead(userId) {
  await Mail.updateMany(
    { ownerId: userId },
    { $set: { read: true }, $addToSet: { labels: 'read' } }
  );
}

/**
 * Helper for building the inbox query.
 */
function inboxQuery(userId) {
  return {
    ownerId:     userId,
    recipientId: userId,
    labels:      { $all: ['inbox'], $nin: ['spam', 'trash', 'archive'] }
  };
}

/**
 * Fetch up to 50 inbox mails for a user, newest first.
 */
async function getInboxForUser(userId) {
  return Mail.find(inboxQuery(userId))
    .sort({ timestamp: -1 })
    .limit(50)
    .lean();
}

/**
 * Search mails for a user by text, across subject and content.
 */
async function searchMails(userId, q) {
  const rx = new RegExp(q, 'i');
  return Mail.find({
    ownerId: userId,
    $or:     [{ subject: rx }, { content: rx }]
  })
    .sort({ timestamp: -1 })
    .lean();
}

/**
 * Search mails within a specific label.
 */
async function searchMailsWithLabel(userId, q, label) {
  const rx = new RegExp(q, 'i');
  return Mail.find({
    ownerId: userId,
    labels:  { $all: [label] },
    $or:     [{ subject: rx }, { content: rx }]
  })
    .sort({ timestamp: -1 })
    .lean();
}

/**
 * Fetch mails for a user under a given label.
 */
async function getEmailsByLabelName(labelName, userId) {
  const base = { ownerId: userId };
  let query;

  switch (labelName) {
    case 'trash':
      query = { ...base, labels: { $all: ['trash'] } };
      break;
    case 'spam':
      query = { ...base, labels: { $all: ['spam'] }, recipientId: userId };
      break;
    case 'inbox':
      query = inboxQuery(userId);
      break;
    case 'sent':
      query = {
        ownerId:  userId,
        senderId: userId,
        labels:   { $all: ['sent'], $nin: ['trash'] }
      };
      break;
    case 'drafts':
      query = {
        ownerId:  userId,
        senderId: userId,
        labels:   { $all: ['drafts'] }
      };
      break;
    default:
      query = {
        ownerId: userId,
        labels:  { $all: [labelName], $nin: ['trash'] }
      };
  }

  return Mail.find(query)
    .sort({ timestamp: -1 })
    .lean();
}

module.exports = {
  createMail,
  saveDraft,
  getMailById,
  deleteMailById,
  clearTrash,
  markAsReadById,
  markAsUnreadByIds,
  markAllAsRead,
  getInboxForUser,
  searchMails,
  searchMailsWithLabel,
  getEmailsByLabelName
};
