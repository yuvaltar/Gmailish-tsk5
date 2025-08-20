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
import com.example.gmailish.data.entity.PendingOperationEntity;
import com.example.gmailish.data.model.PendingOperationType;
import com.example.gmailish.data.repository.MailRepository;
import com.example.gmailish.data.repository.LabelRepository;
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

    public MutableLiveData<String> message = new MutableLiveData<>();
    public MutableLiveData<String> sendSuccess = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MailRepository mailRepo;
    private final LabelRepository labelRepo;
    private final AppDatabase db; // Injected DB for pending ops

    @Inject
    public ComposeViewModel(MailRepository mailRepo, LabelRepository labelRepo, AppDatabase db) {
        this.mailRepo = mailRepo;
        this.labelRepo = labelRepo;
        this.db = db;
    }

    // Public API: send email with current token stored in prefs
    public void sendEmail(Context ctx, String to, String subject, String content) {
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
            sendOnline(ctx, ownerId, to, subject, content);
        } else {
            sendOffline(ctx, ownerId, to, subject, content);
        }
    }

    // ONLINE path
    private void sendOnline(Context ctx, String ownerId, String to, String subject, String content) {
        String jwt = getJwt(ctx);
        if (isEmpty(jwt)) {
            // treat as offline if no token
            sendOffline(ctx, ownerId, to, subject, content);
            return;
        }

        try {
            JSONObject json = new JSONObject();
            JSONArray toArray = new JSONArray().put(to); // adapt if API accepts string
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
                    sendOffline(ctx, ownerId, to, subject, content);
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "sendOnline server code=" + response.code());
                        sendOffline(ctx, ownerId, to, subject, content);
                        return;
                    }
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    String serverId = null;
                    try {
                        JSONObject obj = new JSONObject(bodyStr);
                        serverId = obj.optString("id", obj.optString("_id", UUID.randomUUID().toString()));
                    } catch (Exception ignore) { }

                    final String finalId = (serverId != null && !serverId.isEmpty()) ? serverId : UUID.randomUUID().toString();

                    io.execute(() -> {
                        try {
                            // Save in Room as "sent"
                            mailRepo.saveSentMailLocal(finalId, ownerId, to, subject, content, new Date());
                            mailRepo.ensureLabelAndLink(finalId, ownerId, "sent");
                            message.postValue("Sent");

                            // Emit payload for UI (optional legacy flow)
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
            sendOffline(ctx, ownerId, to, subject, content);
        }
    }

    // OFFLINE path: create Outbox mail + pending MAIL_SEND
    private void sendOffline(Context ctx, String ownerId, String to, String subject, String content) {
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

                // 3) Kick the worker (unique)
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

    // Helpers
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