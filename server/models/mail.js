//models/mail.js
const mongoose = require('mongoose');

const mailSchema = new mongoose.Schema({
  id:             { type: String, required: true, unique: true },
  senderId:       { type: String, required: true },
  senderName:     { type: String, required: true },

  recipientId:    { type: String, required: true },
  recipientName:  { type: String, required: true },
  recipientEmail: { type: String, required: true },

  subject:        { type: String, required: true },
  content:        { type: String, required: true },
  timestamp:      { type: Date, required: true, default: Date.now },

  labels:         { type: [String], required: true, default: [] },
  ownerId:        { type: String, required: true },
  read:           { type: Boolean, required: true, default: false }
});

module.exports = mongoose.model('Mail', mailSchema);