//server/ models/labels
const mongoose = require('mongoose');

const labelSchema = new mongoose.Schema({
  id: {
    type: String,
    required: true,
    unique: true
  },
  ownerId: {
    type: String,
    required: true,
    index: true
  },
  name: {
    type: String,
    required: true,
    trim: true
  }
}, {
  timestamps: true    // adds createdAt / updatedAt
});

module.exports = mongoose.model('Label', labelSchema);
