//labels/routes
const express    = require('express');
const router     = express.Router();
const auth       = require('../middleware/auth');
const ctrl       = require('../controllers/labelsController');

router.use(auth);

router.get('/',           ctrl.getAllLabels);
router.post('/',          ctrl.createLabel);
router.get('/:id',        ctrl.getLabel);
router.patch('/:id',      ctrl.updateLabel);
router.delete('/:id',     ctrl.deleteLabel);
router.get('/:name/emails', ctrl.getEmailsByLabelName);

module.exports = router;
