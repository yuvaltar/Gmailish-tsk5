package com.example.gmailish.data.repository;

import com.example.gmailish.data.dao.LabelDao;
import com.example.gmailish.data.dao.MailLabelDao;
import com.example.gmailish.data.entity.LabelEntity;

import java.util.List;

public class LabelRepository {

    private final LabelDao labelDao;
    private final MailLabelDao mailLabelDao;

    public LabelRepository(LabelDao labelDao, MailLabelDao mailLabelDao) {
        this.labelDao = labelDao;
        this.mailLabelDao = mailLabelDao;
    }

    // Reads (blocking; call on background thread)
    public LabelEntity getLabelByName(String ownerId, String name) {
        return labelDao.getByName(ownerId, name);
    }

    // Writes (blocking)
    public void saveLabel(LabelEntity label) {
        labelDao.upsert(label);
    }

    public void saveLabels(List<LabelEntity> labels) {
        labelDao.upsertAll(labels);
    }

    public int deleteLabel(String labelId) {
        mailLabelDao.clearForLabel(labelId);
        return labelDao.deleteById(labelId);
    }

    // NEW: ensure a label exists for this owner. Returns the id used (lowercased name).
    public String ensureLabel(String ownerId, String labelName) {
        if (labelName == null) return null;
        String id = labelName.toLowerCase();
        LabelEntity existing = labelDao.getByName(ownerId, labelName);
        if (existing == null) {
            labelDao.upsert(new LabelEntity(id, ownerId, labelName));
        }
        return id;
    }
}
