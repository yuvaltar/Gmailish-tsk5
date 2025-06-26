//models/mails
const uuidv4 = require('../utils/uuid');
const { users } = require('./user');

// In-memory storage of all mail records
const mails = [];

/**
 * Create a mail entry in both sender's "sent" and recipient's "inbox".
 * Returns an object with the two created mail records.
 */
function createMail(senderId, recipientId, subject, content, recipientLabels = ['inbox']) {
  const sender = users.find(u => u.id === senderId) || {};
  const recipient = users.find(u => u.id === recipientId) || {};
  const timestamp = new Date().toISOString();

  const senderName = sender.username || 'Unknown';
  const recipientName = recipient.username || 'Unknown';

  // Recipient's copy (inbox)
  const inboxMail = {
    id: uuidv4(),
    senderId,
    senderName,
    recipientId,
    recipientName,
    subject,
    content,
    timestamp,
    labels: recipientLabels,
    ownerId: recipientId,
    read: false
  };

  // Sender's copy (sent)
  const sentMail = {
    id: uuidv4(),
    senderId,
    senderName,
    recipientId,
    recipientName,
    subject,
    content,
    timestamp,
    labels: ['sent'],
    ownerId: senderId,
    read: true  // Sent mails are considered read by default
  };

  // Store both copies
  mails.push(inboxMail, sentMail);

  return { inboxMail, sentMail };
}

/**
 * Find a mail by its ID and owner.
 */
function getMailById(id) {
  return mails.find(m => m.id === id);
}

function deleteMailById(id) {
  const idx = mails.findIndex(m => m.id === id);
  if (idx !== -1) mails.splice(idx, 1);
}

function getInboxForUser(userId) {
  return mails
    .filter(m =>
      m.ownerId === userId &&
      m.recipientId === userId &&
      m.labels.includes('inbox') &&
      !m.labels.includes('spam') &&
      !m.labels.includes('trash')
    )
    .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))
    .slice(0, 50);
}

function searchMailsWithLabel(userId, query, label) {
  const q = query.toLowerCase();
  return mails.filter(m =>
    m.ownerId === userId &&
    m.labels.includes(label) &&
    (
      (m.subject && m.subject.toLowerCase().includes(q)) ||
      (m.content && m.content.toLowerCase().includes(q))
    )
  );
}

function getEmailsByLabelName(labelName, userId) {
  return mails
    .filter(m => {
      if (m.ownerId !== userId) return false;

      const isTrashed = m.labels.includes('trash');
      const isSpam = m.labels.includes('spam');

      if (labelName === 'trash') return isTrashed;
      if (labelName === 'spam') return isSpam && m.recipientId === userId;
      if (labelName === 'inbox')
      return (
        m.recipientId === userId &&
        m.labels.includes('inbox') &&
        !isSpam &&
        !isTrashed &&
        !m.labels.includes('archive') // 👈 EXCLUDE archived mails from inbox
      );
      if (labelName === 'sent') return m.senderId === userId && m.labels.includes('sent') && !isTrashed;
      if (labelName === 'drafts') return m.labels.includes('drafts') && m.senderId === userId;

      return m.labels.includes(labelName) && !isTrashed;
    })
    .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
}

function searchMails(userId, query) {
  const q = query.toLowerCase();
  return mails.filter(m =>
    m.ownerId === userId &&
    (
      (m.subject && m.subject.toLowerCase().includes(q)) ||
      (m.content && m.content.toLowerCase().includes(q))
    )
  );
}

function toggleStar(mailId, userId) {
  const mail = mails.find(m => m.id === mailId && m.ownerId === userId);
  if (!mail) return null;

  mail.starred = !mail.starred;
  mail.labels = mail.labels || [];
  if (mail.starred) {
    if (!mail.labels.includes('starred')) mail.labels.push('starred');
  } else {
    mail.labels = mail.labels.filter(label => label !== 'starred');
  }

  return mail.starred;
}

function markAllAsRead(userId) {
  mails.forEach(mail => {
    if (
      mail.ownerId === userId &&
      !mail.labels.includes('read')
    ) {
      mail.labels.push('read');
      mail.read = true;
    }
  });
}

function markAsReadById(mailId, userId) {
  const mail = mails.find(m => m.id === mailId && m.ownerId === userId);
  if (!mail) return false;

  mail.read = true;
  if (!mail.labels.includes('read')) mail.labels.push('read');
  return true;
}

function markAsUnreadByIds(ids, userId) {
  ids.forEach(id => {
    const mail = mails.find(m => m.id === id && m.ownerId === userId);
    if (mail) {
      mail.read = false;
      mail.labels = mail.labels.filter(l => l !== 'read');
    }
  });
}
module.exports = {
  mails,
  createMail,
  getMailById,
  deleteMailById,
  getInboxForUser,
  getEmailsByLabelName,
  searchMails,
  toggleStar,
  searchMailsWithLabel,
  markAllAsRead,
  markAsReadById,
  markAsUnreadByIds
};
