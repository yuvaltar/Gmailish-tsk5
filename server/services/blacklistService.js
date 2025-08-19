// server/services/blacklistService.js
const Blacklist = require('../models/blacklist');

async function addUrl(url) {
  try {
    const entry = await Blacklist.create({ url });
    return entry.toObject();
  } catch (err) {
    if (err.code === 11000) {
      const e = new Error('URL already blacklisted');
      e.code = 409;
      throw e;
    }
    throw err;
  }
}

async function removeUrl(url) {
  const result = await Blacklist.deleteOne({ url });
  if (result.deletedCount === 0) {
    const e = new Error('URL not found in blacklist');
    e.code = 404;
    throw e;
  }
}

async function isBlacklisted(url) {
  const count = await Blacklist.countDocuments({ url });
  return count > 0;
}

/** NEW: return all urls as array of strings */
async function getAllUrls() {
  const docs = await Blacklist.find({}, { url: 1, _id: 0 }).lean();
  return docs.map(d => d.url);
}

/** NEW: bulk add (dedup, ignore duplicates) */
async function addMany(urls) {
  if (!urls || urls.length === 0) return;
  const ops = urls.map(u => ({
    updateOne: {
      filter: { url: u },
      update: { $setOnInsert: { url: u } },
      upsert: true
    }
  }));
  await Blacklist.bulkWrite(ops, { ordered: false });
}

module.exports = {
  addUrl,
  removeUrl,
  isBlacklisted,
  getAllUrls,   // NEW
  addMany       // NEW
};
