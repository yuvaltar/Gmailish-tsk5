//users/routes
const auth = require('../middleware/auth');

const express = require('express');
const multer  = require('multer');
const router = express.Router();
const usersController = require('../controllers/usersController');

const upload = multer({ dest: 'uploads/' });

router.post('/', upload.single('picture'), usersController.registerUser);
router.get("/by-email/:email", auth, usersController.getUserIdByEmail);
router.get('/:id/picture', usersController.getUserPicture);
router.get('/me', auth, usersController.getCurrentUser);
router.get('/:id', usersController.getUser);

module.exports = router;