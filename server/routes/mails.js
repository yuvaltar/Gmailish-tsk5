const express = require('express');
const router = express.Router();
const mailsController = require('../controllers/mailsController');
const authenticate = require('../middleware/auth');

router.use(authenticate);

//  GET Routes 
router.get('/spam', mailsController.getSpam);
router.get('/search/:label/:query', mailsController.searchMailsByLabel);
router.get('/search/:query', mailsController.searchMails);
router.get('/', mailsController.getInbox);
router.get('/:id', mailsController.getMailById); // 

//POST Routes 
router.post('/draft', mailsController.saveDraft);
router.post('/:id/spam', mailsController.toggleSpam);
router.post('/', mailsController.sendMail);

// PATCH Routes 
router.patch('/markAllRead', mailsController.markAllAsRead);
router.patch('/markUnread', mailsController.markAsUnread);
router.patch('/:id/label', mailsController.addLabelToEmail);
router.patch('/:id/read', mailsController.markAsRead);
router.patch('/:id/star', mailsController.toggleStar);
router.patch('/:id', mailsController.updateMail);

// DELETE Routes 
router.delete('/trash/clear', mailsController.clearTrash);
router.delete('/:id/label/:label', mailsController.removeLabelFromEmail);
router.delete('/:id', mailsController.deleteMail); 

module.exports = router;