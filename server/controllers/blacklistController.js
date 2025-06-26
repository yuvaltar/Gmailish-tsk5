//blacklistcontroller
const { sendToCpp } = require('../services/blacklistService');

// Add a URL to the blacklist
exports.addToBlacklist = async (req, res) => {
  const { id } = req.body;
  if (!id) return res.status(400).json({ error: 'Missing URL' });

  const result = (await sendToCpp(`POST ${id}`)).trim();

  if (result === '201 Created') {
    return res.status(201).json({ message: 'URL added to blacklist' });
  }
  if (result === '409 Conflict') {
    return res.status(409).json({ error: 'URL already blacklisted' });
  }

  return res.status(500).json({ error: 'Unexpected response from C++ server' });
};

// Remove a URL from the blacklist
exports.removeFromBlacklist = async (req, res) => {
  const { id } = req.params;
  const result = (await sendToCpp(`DELETE ${id}`)).trim();

  if (result === '204 No Content') return res.status(200).json({ message: 'URL removed from blacklist' });
  if (result === '404 Not Found') return res.status(404).json({ error: 'URL not found in blacklist' });

  return res.status(500).json({ error: 'Unexpected response from C++ server' });
};
