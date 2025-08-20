package com.example.gmailish.data.sync;

import android.util.Log;

import com.example.gmailish.data.entity.PendingOperationEntity;
import com.example.gmailish.data.repository.PendingOperationRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Singleton
public class PendingSyncManager {

    private static final String TAG = "PendingSyncManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final PendingOperationRepository pendingRepo;
    private final OkHttpClient client = new OkHttpClient();

    @Inject
    public PendingSyncManager(PendingOperationRepository pendingRepo) {
        this.pendingRepo = pendingRepo;
    }

    public void flush(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            Log.w(TAG, "flush: no JWT, skip");
            return;
        }
        List<PendingOperationEntity> ops = pendingRepo.getAllPending();
        Log.d(TAG, "flush: pending count=" + (ops != null ? ops.size() : 0));
        if (ops == null || ops.isEmpty()) return;

        for (PendingOperationEntity op : ops) {
            boolean success = false;
            try {
                JSONObject payload = new JSONObject(op.payloadJson);
                switch (op.type) {
                    case "LABEL_ADD": {
                        String mailId = payload.optString("mailId");
                        String label = payload.optString("label");
                        success = serverAddLabel(mailId, label, jwtToken);
                        break;
                    }
                    case "LABEL_REMOVE": {
                        String mailId = payload.optString("mailId");
                        String label = payload.optString("label");
                        success = serverRemoveLabel(mailId, label, jwtToken);
                        break;
                    }
                    case "LABEL_MOVE": {
                        String mailId = payload.optString("mailId");
                        String target = payload.optString("targetLabel");
                        JSONArray removedArr = payload.optJSONArray("removedLabels");
                        List<String> removed = new ArrayList<>();
                        if (removedArr != null) {
                            for (int i = 0; i < removedArr.length(); i++) removed.add(removedArr.optString(i));
                        }
                        success = serverMove(mailId, target, removed, jwtToken);
                        break;
                    }
                    default:
                        Log.w(TAG, "Unknown pending type: " + op.type);
                }
            } catch (Exception e) {
                Log.e(TAG, "flush parse error for op " + op.id + ": " + e.getMessage(), e);
            }

            try {
                if (success) {
                    pendingRepo.markDone(op.id);
                } else {
                    pendingRepo.incrementRetry(op.id);
                }
            } catch (Exception e) {
                Log.e(TAG, "flush status update error: " + e.getMessage(), e);
            }
        }
    }

    private boolean serverAddLabel(String mailId, String label, String jwtToken) throws IOException {
        JSONObject json = new JSONObject();
        try { json.put("label", label); } catch (Exception ignored) {}
        RequestBody body = RequestBody.create(JSON, json.toString());
        Request req = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        try (Response r = client.newCall(req).execute()) {
            return r.isSuccessful();
        }
    }

    private boolean serverRemoveLabel(String mailId, String label, String jwtToken) throws IOException {
        JSONObject json = new JSONObject();
        try { json.put("label", label); json.put("action", "remove"); } catch (Exception ignored) {}
        RequestBody body = RequestBody.create(JSON, json.toString());
        Request req = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        try (Response r = client.newCall(req).execute()) {
            return r.isSuccessful();
        }
    }

    private boolean serverMove(String mailId, String target, List<String> removed, String jwtToken) throws IOException {
        boolean ok = true;
        if (removed != null) {
            for (String l : removed) {
                if (l == null || l.isEmpty() || "starred".equalsIgnoreCase(l)) continue;
                if (!serverRemoveLabel(mailId, l, jwtToken)) ok = false;
            }
        }
        if (!serverAddLabel(mailId, target, jwtToken)) ok = false;
        return ok;
    }
}
