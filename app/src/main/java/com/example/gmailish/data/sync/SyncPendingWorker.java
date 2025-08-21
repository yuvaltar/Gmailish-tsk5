package com.example.gmailish.data.sync;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gmailish.data.dao.LabelDao;
import com.example.gmailish.data.dao.MailDao;
import com.example.gmailish.data.dao.MailLabelDao;
import com.example.gmailish.data.dao.PendingOperationDao;
import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;
import com.example.gmailish.data.entity.PendingOperationEntity;
import com.example.gmailish.data.model.PendingOperationType;
import com.example.gmailish.data.repository.LabelRepository;
import com.example.gmailish.data.repository.PendingOperationRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.example.gmailish.data.db.AppDbProvider;

/** Processes offline/queued ops when network is available. */
public class SyncPendingWorker extends Worker {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String LABELS_URL = "http://10.0.2.2:3000/api/labels";
    private static final String MAILS_URL  = "http://10.0.2.2:3000/api/mails";

    private final PendingOperationRepository pendingRepo;
    private final LabelRepository labelRepo;
    private final OkHttpClient client;

    private final AppDatabase db;
    private final MailDao mailDao;
    private final MailLabelDao mailLabelDao;

    public SyncPendingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);

        db = AppDbProvider.get(context.getApplicationContext());
        PendingOperationDao pendingDao = db.pendingOperationDao();
        LabelDao labelDao = db.labelDao();
        mailDao = db.mailDao();
        mailLabelDao = db.mailLabelDao();

        this.pendingRepo = new PendingOperationRepository(pendingDao);
        this.labelRepo = new LabelRepository(labelDao, mailLabelDao);
        this.client = new OkHttpClient();
    }

    @NonNull
    @Override
    public Result doWork() {
        List<PendingOperationEntity> ops = pendingRepo.getAllPending();
        for (PendingOperationEntity op : ops) {
            try {
                if (PendingOperationType.LABEL_CREATE.equals(op.type)) {
                    if (!handleLabelCreate(op)) continue;

                } else if (PendingOperationType.MAIL_SEND.equals(op.type)) {
                    if (!handleMailSend(op)) continue;

                } else if (PendingOperationType.DRAFT_SAVE.equals(op.type)) {
                    if (!handleDraftSave(op)) continue;

                } else if (PendingOperationType.DRAFT_SEND.equals(op.type)) {
                    if (!handleDraftSend(op)) continue;
                }
            } catch (IOException ioe) {
                pendingRepo.incrementRetry(op.id);
                return Result.retry();
            } catch (Exception ex) {
                pendingRepo.incrementRetry(op.id);
                return Result.retry();
            }
        }
        return Result.success();
    }

    /* =========================
       LABEL_CREATE
       ========================= */
    private boolean handleLabelCreate(PendingOperationEntity op) throws Exception {
        JSONObject payload = new JSONObject(op.payloadJson);
        String name = payload.optString("name", null);
        String ownerId = payload.optString("ownerId", null);
        String localId = payload.optString("localId", null);
        if (name == null) { pendingRepo.delete(op.id); return false; }

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("prefs", MODE_PRIVATE);
        String jwt = prefs.getString("jwt", "");

        JSONObject body = new JSONObject().put("name", name);
        Request request = new Request.Builder()
                .url(LABELS_URL)
                .header("Authorization", jwt.isEmpty() ? "" : "Bearer " + jwt)
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String resp = response.body() != null ? response.body().string() : "{}";
                JSONObject obj = new JSONObject(resp);
                String serverId = obj.optString("id", obj.optString("_id", localId));
                String serverName = obj.optString("name", name);
                String serverOwnerId = obj.optString("ownerId", ownerId);

                String finalId = serverId != null ? serverId : localId;
                String finalOwner = serverOwnerId != null ? serverOwnerId : (ownerId != null ? ownerId : "");
                labelRepo.saveLabel(new LabelEntity(finalId, finalOwner, serverName));

                if (localId != null && serverId != null && !serverId.equals(localId)) {
                    labelRepo.deleteLabel(localId);
                }
                pendingRepo.markDone(op.id);
            } else {
                int code = response.code();
                if (code == 401 || code == 403 || code >= 500) {
                    pendingRepo.incrementRetry(op.id);
                    return false;
                } else {
                    pendingRepo.delete(op.id);
                }
            }
        }
        return true;
    }

    /* =========================
       MAIL_SEND (Outbox → Sent)
       ========================= */
    private boolean handleMailSend(PendingOperationEntity op) throws Exception {
        JSONObject payload = new JSONObject(op.payloadJson);
        String localId = payload.optString("localId", null);
        String ownerId = payload.optString("ownerId", null);
        String to = payload.optString("to", null);
        String subject = payload.optString("subject", null);
        String content = payload.optString("content", null);

        if (localId == null || ownerId == null || to == null) {
            pendingRepo.delete(op.id);
            return false;
        }

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("prefs", MODE_PRIVATE);
        String jwt = prefs.getString("jwt", "");

        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray().put(to);
        body.put("to", arr);
        body.put("subject", subject != null ? subject : "");
        body.put("content", content != null ? content : "");

        Request req = new Request.Builder()
                .url(MAILS_URL)
                .header("Authorization", jwt.isEmpty() ? "" : "Bearer " + jwt)
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        try (Response response = client.newCall(req).execute()) {
            if (response.isSuccessful()) {
                String resp = response.body() != null ? response.body().string() : "{}";
                String serverId;
                try {
                    JSONObject obj = new JSONObject(resp);
                    serverId = obj.optString("id", obj.optString("_id", UUID.randomUUID().toString()));
                } catch (Exception ignore) {
                    serverId = UUID.randomUUID().toString();
                }
                final String finalId = (serverId != null && !serverId.isEmpty()) ? serverId : localId;

                MailEntity local = mailDao.getByIdSync(localId);
                if (local == null) {
                    local = new MailEntity(
                            finalId, ownerId, "Me",
                            to, to, to,
                            subject != null ? subject : "",
                            content != null ? content : "",
                            new Date(), ownerId,
                            true, false
                    );
                    mailDao.upsert(local);
                } else {
                    MailEntity serverMail = new MailEntity(
                            finalId,
                            local.getSenderId(), local.getSenderName(),
                            local.getRecipientId(), local.getRecipientName(), local.getRecipientEmail(),
                            local.getSubject(), local.getContent(),
                            local.getTimestamp(), local.getOwnerId(),
                            true, local.getStarred()
                    );
                    mailLabelDao.clearForMail(localId);
                    mailDao.deleteById(localId);
                    mailDao.upsert(serverMail);
                }

                try { mailLabelDao.remove(finalId, "outbox"); } catch (Exception ignore) {}
                labelRepo.saveLabel(new LabelEntity("sent", ownerId, "sent"));
                mailLabelDao.add(new MailLabelCrossRef(finalId, "sent"));

                pendingRepo.markDone(op.id);
            } else {
                int code = response.code();
                if (code == 401 || code == 403 || code >= 500) {
                    pendingRepo.incrementRetry(op.id);
                    return false;
                } else {
                    pendingRepo.delete(op.id);
                }
            }
        }
        return true;
    }

    /* =========================
       DRAFT_SAVE (new)
       ========================= */
    private boolean handleDraftSave(PendingOperationEntity op) {
        // If you don’t sync drafts to server yet, just mark done so the queue clears.
        // (Your draft is already saved locally by the caller.)
        pendingRepo.markDone(op.id);
        return true;
    }

    /* =========================
       DRAFT_SEND (new)
       ========================= */
    private boolean handleDraftSend(PendingOperationEntity op) throws Exception {
        // Expected payload fields:
        // { "draftId": "...", "ownerId": "...", "to": "...", "subject": "...", "content": "..." }
        JSONObject payload = new JSONObject(op.payloadJson);
        String draftId = payload.optString("draftId", null);
        String ownerId = payload.optString("ownerId", null);
        String to      = payload.optString("to", null);
        String subject = payload.optString("subject", null);
        String content = payload.optString("content", null);

        if (draftId == null || ownerId == null || to == null) {
            // malformed
            pendingRepo.delete(op.id);
            return false;
        }

        // Send to server exactly like MAIL_SEND
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("prefs", MODE_PRIVATE);
        String jwt = prefs.getString("jwt", "");

        JSONObject body = new JSONObject();
        body.put("to", new JSONArray().put(to));
        body.put("subject", subject != null ? subject : "");
        body.put("content", content != null ? content : "");

        Request req = new Request.Builder()
                .url(MAILS_URL)
                .header("Authorization", jwt.isEmpty() ? "" : "Bearer " + jwt)
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        try (Response response = client.newCall(req).execute()) {
            if (response.isSuccessful()) {
                String resp = response.body() != null ? response.body().string() : "{}";
                String serverId;
                try {
                    JSONObject obj = new JSONObject(resp);
                    serverId = obj.optString("id", obj.optString("_id", UUID.randomUUID().toString()));
                } catch (Exception ignore) {
                    serverId = UUID.randomUUID().toString();
                }

                final String finalId = (serverId != null && !serverId.isEmpty()) ? serverId : draftId;

                // 1) Remove the local draft
                try { mailLabelDao.clearForMail(draftId); } catch (Exception ignore) {}
                try { mailDao.deleteById(draftId); } catch (Exception ignore) {}

                // 2) Create the local "sent" record
                MailEntity sent = new MailEntity(
                        finalId,
                        ownerId, "Me",
                        to, to, to,
                        subject != null ? subject : "",
                        content != null ? content : "",
                        new Date(),
                        ownerId,
                        true, false
                );
                mailDao.upsert(sent);
                labelRepo.saveLabel(new LabelEntity("sent", ownerId, "sent"));
                mailLabelDao.add(new MailLabelCrossRef(finalId, "sent"));

                // 3) Done
                pendingRepo.markDone(op.id);
            } else {
                int code = response.code();
                if (code == 401 || code == 403 || code >= 500) {
                    pendingRepo.incrementRetry(op.id);
                    return false;
                } else {
                    // permanent client error -> drop
                    pendingRepo.delete(op.id);
                }
            }
        }
        return true;
    }

    /* =========================
       Enqueue unique work
       ========================= */
    public static void enqueue(Context context) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncPendingWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("sync-pending")
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-pending-once",
                ExistingWorkPolicy.KEEP,
                req
        );
    }

    /** Local DB holder (singleton). */
    private static final class DbHolder {
        private static volatile AppDatabase INSTANCE;
        static AppDatabase getInstance(Context context) {
            if (INSTANCE == null) {
                synchronized (DbHolder.class) {
                    if (INSTANCE == null) {
                        INSTANCE = androidx.room.Room.databaseBuilder(
                                        context.getApplicationContext(),
                                        AppDatabase.class,
                                        "gmailish.db"
                                )
                                .fallbackToDestructiveMigration()
                                .build();
                    }
                }
            }
            return INSTANCE;
        }
    }
}
