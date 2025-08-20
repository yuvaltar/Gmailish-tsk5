package com.example.gmailish.data.repository;

import com.example.gmailish.data.dao.PendingOperationDao;
import com.example.gmailish.data.entity.PendingOperationEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class PendingOperationRepository {

    private final PendingOperationDao dao;

    public PendingOperationRepository(PendingOperationDao dao) {
        this.dao = dao;
    }

    public void enqueue(PendingOperationEntity op) {
        dao.upsert(op);
    }

    public List<PendingOperationEntity> getAllPending() {
        return dao.getAllPending();
    }

    public void markDone(String id) {
        dao.markDone(id);
    }

    public void incrementRetry(String id) {
        dao.incrementRetry(id);
    }

    public void delete(String id) {
        dao.delete(id);
    }

    // Convenience helpers to structure payload JSON

    public void enqueueLabelAdd(String mailId, String label) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("mailId", mailId);
            payload.put("label", label);
            enqueue(new PendingOperationEntity(
                    UUID.randomUUID().toString(),
                    "LABEL_ADD",
                    payload.toString(),
                    new Date(),
                    0,
                    "PENDING",
                    null
            ));
        } catch (Exception ignored) {}
    }

    public void enqueueLabelRemove(String mailId, String label) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("mailId", mailId);
            payload.put("label", label);
            enqueue(new PendingOperationEntity(
                    UUID.randomUUID().toString(),
                    "LABEL_REMOVE",
                    payload.toString(),
                    new Date(),
                    0,
                    "PENDING",
                    null
            ));
        } catch (Exception ignored) {}
    }

    public void enqueueLabelMove(String mailId, String targetLabel, List<String> removedLabels) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("mailId", mailId);
            payload.put("targetLabel", targetLabel);
            JSONArray removed = new JSONArray();
            if (removedLabels != null) for (String l : removedLabels) removed.put(l);
            payload.put("removedLabels", removed);
            enqueue(new PendingOperationEntity(
                    UUID.randomUUID().toString(),
                    "LABEL_MOVE",
                    payload.toString(),
                    new Date(),
                    0,
                    "PENDING",
                    null
            ));
        } catch (Exception ignored) {}
    }
}
