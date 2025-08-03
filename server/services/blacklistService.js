// server/services/blacklistService.js
const Blacklist = require('../models/blacklist');

/**
 * Add a URL to the blacklist.
 * Throws if already exists.
 */
async function addUrl(url) {
  try {
    const entry = await Blacklist.create({ url });
    return entry.toObject();
  } catch (err) {
    // Duplicate key
    if (err.code === 11000) {
      const e = new Error('URL already blacklisted');
      e.code = 409;
      throw e;
    }
    throw err;
  }
}

/**
 * Remove a URL from the blacklist.
 * Throws if not found.
 */
async function removeUrl(url) {
  const result = await Blacklist.deleteOne({ url });
  if (result.deletedCount === 0) {
    const e = new Error('URL not found in blacklist');
    e.code = 404;
    throw e;
  }
}

/**
 * Check if a URL is blacklisted.
 * Returns true/false.
 */
async function isBlacklisted(url) {
  const count = await Blacklist.countDocuments({ url });
  return count > 0;
}

module.exports = {
  addUrl,
  removeUrl,
  isBlacklisted
};
