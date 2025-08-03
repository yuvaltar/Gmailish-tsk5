// server/controllers/blacklistController.js
const {
  addUrl,
  removeUrl
} = require('../services/blacklistService');

// POST /api/blacklist
exports.addToBlacklist = async (req, res) => {
  const { id: url } = req.body;
  if (!url) {
    return res.status(400).json({ error: 'Missing URL' });
  }
  try {
    await addUrl(url);
    return res.status(201).json({ message: 'URL added to blacklist' });
  } catch (err) {
    if (err.code === 409) {
      return res.status(409).json({ error: err.message });
    }
    console.error('addToBlacklist error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};

// DELETE /api/blacklist/:id
exports.removeFromBlacklist = async (req, res) => {
  const url = req.params.id;
  try {
    await removeUrl(url);
    return res.status(200).json({ message: 'URL removed from blacklist' });
  } catch (err) {
    if (err.code === 404) {
      return res.status(404).json({ error: err.message });
    }
    console.error('removeFromBlacklist error:', err);
    return res.status(500).json({ error: 'Internal server error' });
  }
};
