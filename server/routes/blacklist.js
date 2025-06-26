//routes/ blacklist
const express = require('express');
const router = express.Router();
const blacklistController = require('../controllers/blacklistController');

const authenticate = require('../middleware/auth');
router.use(authenticate); 


// POST /api/blacklist - Add a URL to the blacklist
router.post('/', blacklistController.addToBlacklist);

// DELETE /api/blacklist/:id - Remove a URL from the blacklist
router.delete('/:id', blacklistController.removeFromBlacklist);

module.exports = router;
