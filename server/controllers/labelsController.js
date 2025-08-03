//labelscontroller
const mailsService  = require('../services/mail');      // for getEmailsByLabelName
const labelsService = require('../services/labels');

exports.getAllLabels = async (req, res) => {
  try {
    const labels = await labelsService.getAllLabels(req.user.id);
    res.json(labels);
  } catch (err) {
    console.error('getAllLabels error:', err);
    res.status(500).json({ error: 'Could not fetch labels' });
  }
};

exports.getLabel = async (req, res) => {
  try {
    const label = await labelsService.getLabelById(req.params.id, req.user.id);
    res.json(label);
  } catch (err) {
    const code = err.message === 'Label not found' ? 404 : 500;
    res.status(code).json({ error: err.message });
  }
};

exports.createLabel = async (req, res) => {
  try {
    const label = await labelsService.createLabel(req.user.id, req.body.name);
    res.status(201)
       .location(`/api/labels/${label.id}`)
       .json(label);
  } catch (err) {
    const code = err.message === 'Name is required' ? 400 : 500;
    res.status(code).json({ error: err.message });
  }
};

exports.updateLabel = async (req, res) => {
  try {
    await labelsService.updateLabel(req.params.id, req.user.id, req.body.name);
    res.sendStatus(204);
  } catch (err) {
    const code = err.message === 'Name is required' ? 400 :
                 err.message === 'Label not found'  ? 404 : 500;
    res.status(code).json({ error: err.message });
  }
};

exports.deleteLabel = async (req, res) => {
  try {
    await labelsService.deleteLabel(req.params.id, req.user.id);
    res.sendStatus(204);
  } catch (err) {
    const code = err.message === 'Label not found' ? 404 : 500;
    res.status(code).json({ error: err.message });
  }
};

exports.getEmailsByLabelName = async (req, res) => {
  try {
    const emails = await mailsService.getEmailsByLabelName(
      req.params.name,
      req.user.id
    );
    res.json(emails);
  } catch (err) {
    console.error('getEmailsByLabelName error:', err);
    res.status(500).json({ error: 'Could not fetch emails for that label' });
  }
};
