//servises/labels
const Label  = require('../models/label');
const uuidv4 = require('../utils/uuid');

async function getAllLabels(ownerId) {
  return Label.find({ ownerId })
    .sort({ name: 1 })
    .lean();
}

async function getLabelById(id, ownerId) {
  const lab = await Label.findOne({ id, ownerId }).lean();
  if (!lab) throw new Error('Label not found');
  return lab;
}

async function createLabel(ownerId, name) {
  if (!name || !name.trim()) {
    throw new Error('Name is required');
  }
  const lab = await Label.create({
    id:      uuidv4(),
    ownerId,
    name:    name.trim()
  });
  return lab.toObject();
}

async function updateLabel(id, ownerId, name) {
  if (!name || !name.trim()) {
    throw new Error('Name is required');
  }
  const result = await Label.updateOne(
    { id, ownerId },
    { $set: { name: name.trim() } }
  );
  if (result.matchedCount === 0) {
    throw new Error('Label not found');
  }
}

async function deleteLabel(id, ownerId) {
  const result = await Label.deleteOne({ id, ownerId });
  if (result.deletedCount === 0) {
    throw new Error('Label not found');
  }
}

module.exports = {
  getAllLabels,
  getLabelById,
  createLabel,
  updateLabel,
  deleteLabel
};
