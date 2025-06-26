//labelscontroller.js
const uuidv4 = require('../utils/uuid');
const { mails } = require('../models/mail');
const userLabels = {};  // Fixed: correctly scoped per user

// GET /api/labels
exports.getAllLabels = (req, res) => {
  const userId = req.user.id;
  res.status(200).json(userLabels[userId] || []);
};

// POST /api/labels
exports.createLabel = (req, res) => {
  const { name } = req.body;
  const userId = req.user.id;

  if (!name) return res.status(400).json({ error: 'Name is required' });

  const newLabel = { id: uuidv4(), name };
  if (!userLabels[userId]) userLabels[userId] = [];
  userLabels[userId].push(newLabel);
  res.status(201).location(`/api/labels/${newLabel.id}`).end();
};

// GET /api/labels/:id
exports.getLabel = (req, res) => {
  const userId = req.user.id;
  const label = (userLabels[userId] || []).find(l => l.id === req.params.id);

  if (!label) return res.status(404).json({ error: 'Label not found' });
  res.status(200).json(label);
};

// PATCH /api/labels/:id
exports.updateLabel = (req, res) => {
  const userId = req.user.id;
  const label = (userLabels[userId] || []).find(l => l.id === req.params.id);

  if (!label) return res.status(404).json({ error: 'Label not found' });

  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'Name is required' });

  label.name = name;
  res.status(204).end();
};

// DELETE /api/labels/:id
exports.deleteLabel = (req, res) => {
  const userId = req.user.id;
  const userLabelList = userLabels[userId] || [];
  const index = userLabelList.findIndex(l => l.id === req.params.id);

  if (index === -1) return res.status(404).json({ error: 'Label not found' });

  userLabelList.splice(index, 1);
  res.status(204).end();
};

// GET /api/labels/:name/emails
exports.getEmailsByLabelName = (req, res) => {
  const labelName = req.params.name;
  const userId = req.user.id;

  const labeledEmails = mails.filter(email =>
    (email.recipientId === userId || email.senderId === userId) &&
    email.labels?.includes(labelName)
  );

  res.status(200).json(labeledEmails);
};
