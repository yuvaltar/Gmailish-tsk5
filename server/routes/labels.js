//routes/labels
const express = require('express');
const router = express.Router();
const labelsController = require('../controllers/labelsController');
const authenticate = require('../middleware/auth');

router.use(authenticate);

// GET /api/labels - list all labels
router.get('/', labelsController.getAllLabels);

// POST /api/labels - create new label
router.post('/', labelsController.createLabel);

// GET api/labels/:name - Get all emails with a given label name
router.get('/:name/emails', labelsController.getEmailsByLabelName);

// GET /api/labels/:id - get specific label
router.get('/:id', labelsController.getLabel);

// PATCH /api/labels/:id - update label
router.patch('/:id', labelsController.updateLabel);

// DELETE /api/labels/:id - delete label
router.delete('/:id', labelsController.deleteLabel);

module.exports = router;
