package com.example.gmailish.ui.compose;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.PendingOperationEntity;
import com.example.gmailish.data.model.PendingOperationType;
import com.example.gmailish.data.repository.LabelRepository;
import com.example.gmailish.data.repository.MailRepository;
import com.example.gmailish.data.sync.SyncPendingWorker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@HiltViewModel
public class ComposeViewModel extends ViewModel {

    private static final String TAG = "ComposeVM";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public final MutableLiveData<String> message = new MutableLiveData<>();
    public final MutableLiveData<String> sendSuccess = new MutableLiveData<>();

    /** Emits the active draft id after save/update so the Activity can keep it */
    public final MutableLiveData<String> draftIdLive = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MailRepository mailRepo;
    private final LabelRepository labelRepo;
    private final AppDatabase db;

    @Inject
    public ComposeViewModel(MailRepository mailRepo, LabelRepository labelRepo, AppDatabase db) {
        this.mailRepo = mailRepo;
        this.labelRepo = labelRepo;
        this.db = db;
    }

    /* =========================
       DRAFTS
       ========================= */

    /** Save or update a draft. Returns/Publishes the draft id. */
    public void saveOrUpdateDraft(Context ctx,
                                  String existingDraftIdOrNull,
                                  String to,
                                  String subject,
                                  String content) {
        String ownerId = getOwnerId(ctx);
        if (isEmpty(ownerId)) {
            message.setValue("Missing current user");
            return;
        }

        // Don't create empty drafts
        if (isEmpty(to) && isEmpty(subject) && isEmpty(content)) {
            message.setValue("Nothing to save");
            return;
        }

        final String draftId = (existingDraftIdOrNull != null && !existingDraftIdOrNull.isEmpty())
                ? existingDraftIdOrNull
                : "draft-" + UUID.randomUUID();

        io.execute(() -> {
            try {
                Date now = new Date();

                // We save a regular MailEntity owned by the current user and link it to the "drafts" label.
                MailEntity draft = new MailEntity(
                        draftId,
                        ownerId,              // senderId (me)
                        "Me",                 // senderName (displayed locally)
                        to != null ? to : "", // recipientId (we store the email as id for now)
                        to,                   // recipientName (fallback to email)
                        to,                   // recipientEmail
                        subject,
                        content,
                        now,
                        ownerId,
                        true,                 // read
                        false                 // starred
                );

                mailRepo.saveMail(draft);
                mailRepo.ensureLabelAndLink(draftId, ownerId, "drafts");

                draftIdLive.postValue(draftId);
                message.postValue("Draft saved");
            } catch (Exception e) {
                Log.e(TAG, "saveOrUpdateDraft error: " + e.getMessage(), e);
                message.postValue("Failed to save draft");
            }
        });
    }

    /** Remove a draft completely (used if user discards). */
    public void discardDraft(Context ctx, String draftId) {
        if (isEmpty(draftId)) return;
        io.execute(() -> {
            try {
                mailRepo.deleteMail(draftId);
                message.postValue("Draft discarded");
            } catch (Exception e) {
                Log.e(TAG, "discardDraft error: " + e.getMessage(), e);
                message.postValue("Failed to discard draft");
            }
        });
    }

    /* =========================
       SENDING
       ========================= */

    /** Original API (no draft id). */
    public void sendEmail(Context ctx, String to, String subject, String content) {
        sendEmail(ctx, to, subject, content, null);
    }

    /** New API: if draftId != null we will delete the draft after a successful send. */
    public void sendEmail(Context ctx, String to, String subject, String content, String draftId) {
        if (isEmpty(to) || isEmpty(subject) || isEmpty(content)) {
            message.setValue("Please fill all fields");
            return;
        }
        String ownerId = getOwnerId(ctx);
        if (isEmpty(ownerId)) {
            message.setValue("Missing current user");
            return;
        }
        if (isOnline(ctx)) {
            sendOnline(ctx, ownerId, to, subject, content, draftId);
        } else {
            sendOffline(ctx, ownerId, to, subject, content, draftId);
        }
    }

    // ONLINE path
    private void sendOnline(Context ctx, String ownerId, String to, String subject, String content, String draftId) {
        String jwt = getJwt(ctx);
        if (isEmpty(jwt)) {
            // treat as offline if no token
            sendOffline(ctx, ownerId, to, subject, content, draftId);
            return;
        }

        try {
            JSONObject json = new JSONObject();
            JSONArray toArray = new JSONArray().put(to); // adapt if server expects array
            json.put("to", toArray);
            json.put("subject", subject);
            json.put("content", content);

            Request req = new Request.Builder()
                    .url("http://10.0.2.2:3000/api/mails")
                    .header("Authorization", "Bearer " + jwt)
                    .post(RequestBody.create(JSON, json.toString()))
                    .build();

            client.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "sendOnline network error: " + e.getMessage());
                    sendOffline(ctx, ownerId, to, subject, content, draftId);
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "sendOnline server code=" + response.code());
                        sendOffline(ctx, ownerId, to, subject, content, draftId);
                        return;
                    }
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    String serverId = null;
                    try {
                        JSONObject obj = new JSONObject(bodyStr);
                        serverId = obj.optString("id", obj.optString("_id", UUID.randomUUID().toString()));
                    } catch (Exception ignore) { }

                    final String finalId = (serverId != null && !serverId.isEmpty())
                            ? serverId : UUID.randomUUID().toString();

                    io.execute(() -> {
                        try {
                            // Save in Room as "sent"
                            mailRepo.saveSentMailLocal(finalId, ownerId, to, subject, content, new Date());
                            mailRepo.ensureLabelAndLink(finalId, ownerId, "sent");

                            // If we sent from a draft, remove it
                            if (draftId != null && !draftId.isEmpty()) {
                                try { mailRepo.deleteMail(draftId); } catch (Exception ignore) {}
                            }

                            message.postValue("Sent");

                            // Emit payload (legacy UI hook)
                            try {
                                JSONObject emitted = new JSONObject();
                                emitted.put("id", finalId);
                                emitted.put("to", to);
                                emitted.put("subject", subject);
                                emitted.put("content", content);
                                sendSuccess.postValue(emitted.toString());
                            } catch (Exception ignore) { }
                        } catch (Exception ex) {
                            Log.e(TAG, "save sent locally error: " + ex.getMessage(), ex);
                            message.postValue("Sent (local save failed)");
                        }
                    });
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "sendOnline JSON build error: " + ex.getMessage(), ex);
            sendOffline(ctx, ownerId, to, subject, content, draftId);
        }
    }

    // OFFLINE path: create Outbox mail + pending MAIL_SEND
    private void sendOffline(Context ctx, String ownerId, String to, String subject, String content, String draftId) {
        final String localId = "local-" + UUID.randomUUID();
        io.execute(() -> {
            try {
                // 1) Save mail locally as outbox
                mailRepo.saveOutboxMailLocal(localId, ownerId, to, subject, content, new Date());
                mailRepo.ensureLabelAndLink(localId, ownerId, "outbox");

                // 2) Enqueue pending MAIL_SEND directly via injected DB
                JSONObject payload = new JSONObject();
                payload.put("localId", localId);
                payload.put("ownerId", ownerId);
                payload.put("to", to);
                payload.put("subject", subject);
                payload.put("content", content);

                db.pendingOperationDao().upsert(new PendingOperationEntity(
                        UUID.randomUUID().toString(),
                        PendingOperationType.MAIL_SEND,
                        payload.toString(),
                        new Date(System.currentTimeMillis()),
                        0,
                        "PENDING",
                        localId
                ));

                // 3) If we sent from a draft, remove it now (it’s queued for sending)
                if (draftId != null && !draftId.isEmpty()) {
                    try { mailRepo.deleteMail(draftId); } catch (Exception ignore) {}
                }

                // 4) Kick the worker
                SyncPendingWorker.enqueue(ctx);

                message.postValue("Saved to Outbox (queued for send)");

                try {
                    JSONObject emitted = new JSONObject();
                    emitted.put("id", localId);
                    emitted.put("to", to);
                    emitted.put("subject", subject);
                    emitted.put("content", content);
                    sendSuccess.postValue(emitted.toString());
                } catch (Exception ignore) { }
            } catch (Exception e) {
                Log.e(TAG, "sendOffline error: " + e.getMessage(), e);
                message.postValue("Failed to save to Outbox");
            }
        });
    }

    /* =========================
       Helpers
       ========================= */

    private boolean isOnline(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }

    private String getJwt(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return prefs.getString("jwt", null);
    }

    private String getOwnerId(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_id", null);
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
