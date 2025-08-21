package com.example.gmailish.mail;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.data.mappers.MailMapper;
import com.example.gmailish.data.repository.MailRepository;
import com.example.gmailish.data.repository.PendingOperationRepository;
import com.example.gmailish.data.entity.MailEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
public class MailViewModel extends ViewModel {

    private static final String TAG = "MailVM";

    public MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public MutableLiveData<JSONObject> mailData = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private List<JSONObject> userLabels = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private String cachedOwnerId;

    private final MailRepository mailRepository;
    private final PendingOperationRepository pendingRepo;

    @Inject
    public MailViewModel(MailRepository mailRepository, PendingOperationRepository pendingRepo) {
        this.mailRepository = mailRepository;
        this.pendingRepo = pendingRepo;
    }

    /* =========================
       Label helpers
       ========================= */

    /** Local/Room/UI canonical: use "primary" instead of "inbox". */
    private String normalizeLabelLocal(String label) {
        if (label == null) return null;
        String v = label.trim();
        if (v.equalsIgnoreCase("inbox")) return "primary";
        return v.toLowerCase();
    }

    /** Server API canonical: use "inbox" instead of "primary". */
    private String apiLabel(String label) {
        if (label == null) return null;
        return "primary".equalsIgnoreCase(label) ? "inbox" : label.toLowerCase(java.util.Locale.ROOT);
    }

    public void setUserLabels(List<JSONObject> labels) {
        this.userLabels = labels != null ? labels : new ArrayList<>();
    }

    public List<String> getUserLabelNames() {
        List<String> names = new ArrayList<>();
        for (JSONObject label : userLabels) {
            names.add(label.optString("name"));
        }
        return names;
    }

    private String getOwnerId(Context ctx) {
        if (cachedOwnerId != null) return cachedOwnerId;
        SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        cachedOwnerId = prefs.getString("user_id", null);
        Log.d(TAG, "getOwnerId -> " + cachedOwnerId);
        return cachedOwnerId;
    }

    /* =========================
       Detail loading
       ========================= */

    public void loadMailDetail(Context appContext, String mailId, String jwtToken) {
        // 1) local immediately
        ioExecutor.execute(() -> {
            try {
                MailEntity local = mailRepository.getByIdSync(mailId);
                if (local != null) {
                    List<String> labels = mailRepository.getLabelsForMailSync(mailId);
                    // ensure local labels are "primary" not "inbox"
                    List<String> fixed = new ArrayList<>();
                    for (String l : labels) fixed.add(normalizeLabelLocal(l));
                    JSONObject localJson = mailRepository.buildMailJson(local, fixed);
                    if (localJson != null) {
                        mailData.postValue(localJson);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadMailDetail local error: " + e.getMessage(), e);
            }
        });

        // 2) network refresh (if token present)
        if (jwtToken == null || jwtToken.isEmpty()) {
            Log.w(TAG, "loadMailDetail: no JWT, skipping network");
            return;
        }
        fetchMailByIdAndCache(appContext, mailId, jwtToken);
    }

    private void fetchMailByIdAndCache(Context appContext, String mailId, String jwtToken) {
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId)
                .get()
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchMailById network failure: " + e.getMessage());
                errorMessage.postValue("Failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    Log.d(TAG, "fetchMailById response code=" + r.code());
                    if (!r.isSuccessful()) {
                        errorMessage.postValue("Error: " + r.code());
                        return;
                    }
                    try {
                        String body = r.body() != null ? r.body().string() : "";
                        Log.v(TAG, "fetchMailById body: " + body);
                        JSONObject mailJson = new JSONObject(body);

                        // Force labels to local canonical ("primary" instead of "inbox") for UI
                        JSONArray labels = mailJson.optJSONArray("labels");
                        if (labels != null) {
                            JSONArray fixed = new JSONArray();
                            for (int i = 0; i < labels.length(); i++) {
                                fixed.put(normalizeLabelLocal(labels.optString(i)));
                            }
                            mailJson.put("labels", fixed);
                        }

                        // Post to UI
                        mailData.postValue(mailJson);

                        // Cache to Room
                        ioExecutor.execute(() -> {
                            try {
                                MailEntity entity = MailMapper.mailEntityFromJson(mailJson);
                                List<String> labelsLocal = MailMapper.labelIdsFromJson(mailJson);
                                // upsert mail
                                mailRepository.saveMail(entity);
                                // replace label links
                                String ownerId = entity.getOwnerId();
                                mailRepository.replaceMailLabels(
                                        entity.getId(), labelsLocal, ownerId != null ? ownerId : "");
                            } catch (Exception ex) {
                                Log.e(TAG, "caching mail error: " + ex.getMessage(), ex);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "fetchMailById parse error: " + e.getMessage());
                        errorMessage.postValue("Parsing error");
                    }
                }
            }
        });
    }

    // Compatibility method
    public void fetchMailById(String mailId, String jwtToken) {
        fetchMailByIdAndCache(null, mailId, jwtToken);
    }

    public void markAsRead(String mailId, String jwtToken) {
        Log.d(TAG, "markAsRead: mailId=" + mailId + " hasToken=" + (jwtToken != null));
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/read")
                .patch(RequestBody.create(null, new byte[0]))
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "markAsRead network error: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    Log.d(TAG, "markAsRead response code=" + r.code());
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "markAsRead failed: " + r.code());
                        return;
                    }
                    try {
                        JSONObject cur = mailData.getValue();
                        if (cur != null) {
                            cur.put("read", true);
                            mailData.postValue(cur);
                        }
                    } catch (Exception ignored) { }
                    // Cache local state
                    ioExecutor.execute(() -> {
                        try {
                            mailRepository.setRead(mailId, true);
                        } catch (Exception e) {
                            Log.e(TAG, "markAsRead local update error: " + e.getMessage(), e);
                        }
                    });
                }
            }
        });
    }

    /* =========================
       Label mutations (fixed server/local mapping)
       ========================= */

    public void addLabel(String mailId, String label, String jwtToken, Context appContext) {
        String labelLocal = normalizeLabelLocal(label); // for Room/UI
        String labelServer = apiLabel(labelLocal);      // for REST
        Log.d(TAG, "addLabel: mailId=" + mailId + " labelLocal=" + labelLocal + " labelServer=" + labelServer);

        JSONObject json = new JSONObject();
        try { json.put("label", labelServer); }
        catch (Exception e) { Log.e(TAG, "addLabel JSON build error: " + e.getMessage()); errorMessage.postValue("Add label JSON error"); return; }

        RequestBody body = RequestBody.create(JSON, json.toString());
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "addLabel network failure: " + e.getMessage());
                errorMessage.postValue("Add label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    Log.d(TAG, "addLabel server response code=" + r.code());
                    if (!r.isSuccessful()) {
                        errorMessage.postValue("Add label failed: " + r.code());
                        return;
                    }
                    ioExecutor.execute(() -> {
                        try {
                            String ownerId = getOwnerId(appContext);
                            mailRepository.addLabelToMailLocal(mailId, labelLocal, ownerId != null ? ownerId : "", labelLocal);
                            tryUpdateLabelsInUi(mailId, true, labelLocal);
                        } catch (Exception ex) {
                            Log.e(TAG, "addLabel: local Room add error: " + ex.getMessage(), ex);
                        }
                    });
                }
            }
        });
    }

    public void removeLabel(String mailId, String label, String jwtToken, Context appContext) {
        String labelLocal = normalizeLabelLocal(label); // for Room/UI
        String labelServer = apiLabel(labelLocal);      // for REST
        Log.d(TAG, "removeLabel: mailId=" + mailId + " labelLocal=" + labelLocal + " labelServer=" + labelServer);

        JSONObject json = new JSONObject();
        try { json.put("label", labelServer); json.put("action", "remove"); }
        catch (Exception e) { Log.e(TAG, "removeLabel JSON build error: " + e.getMessage()); errorMessage.postValue("Remove label JSON error"); return; }

        RequestBody body = RequestBody.create(JSON, json.toString());
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "removeLabel network failure: " + e.getMessage());
                errorMessage.postValue("Remove label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    Log.d(TAG, "removeLabel server response code=" + r.code());
                    if (!r.isSuccessful()) {
                        errorMessage.postValue("Remove label failed: " + r.code());
                        return;
                    }
                    ioExecutor.execute(() -> {
                        try {
                            mailRepository.removeLabelFromMailLocal(mailId, labelLocal);
                            tryUpdateLabelsInUi(mailId, false, labelLocal);
                        } catch (Exception ex) {
                            Log.e(TAG, "removeLabel: local Room remove error: " + ex.getMessage(), ex);
                        }
                    });
                }
            }
        });
    }

    public void addOrRemoveLabel(String mailId, String label, String jwtToken, boolean shouldAdd, Context appContext) {
        Log.d(TAG, "addOrRemoveLabel: mailId=" + mailId + " label=" + label + " shouldAdd=" + shouldAdd);
        if (shouldAdd) addLabel(mailId, label, jwtToken, appContext);
        else removeLabel(mailId, label, jwtToken, appContext);
    }

    public void removeLabelWithCallback(String mailId, String label, String jwtToken, Runnable onSuccess) {
        String labelLocal = normalizeLabelLocal(label);
        String labelServer = apiLabel(labelLocal);
        Log.d(TAG, "removeLabelWithCallback: mailId=" + mailId + " labelLocal=" + labelLocal + " labelServer=" + labelServer);

        JSONObject json = new JSONObject();
        try { json.put("label", labelServer); json.put("action", "remove"); }
        catch (Exception e) { Log.e(TAG, "removeLabelWithCallback JSON build error: " + e.getMessage()); errorMessage.postValue("Remove label JSON error"); return; }

        RequestBody body = RequestBody.create(JSON, json.toString());
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "removeLabelWithCallback network failure: " + e.getMessage());
                errorMessage.postValue("Remove label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    Log.d(TAG, "removeLabelWithCallback server response code=" + r.code());
                    if (r.isSuccessful()) onSuccess.run();
                    else errorMessage.postValue("Remove label failed: " + r.code());
                }
            }
        });
    }

    public void deleteMail(String mailId, String jwtToken, Context appContext) {
        Log.d(TAG, "deleteMail: mailId=" + mailId + " hasToken=" + (jwtToken != null));
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId)
                .delete()
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "deleteMail network failure: " + e.getMessage());
                errorMessage.postValue("Delete failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    int code = r.code();
                    Log.d(TAG, "deleteMail server response code=" + code);
                    if (!r.isSuccessful()) {
                        errorMessage.postValue("Delete failed: " + code);
                        return;
                    }
                    ioExecutor.execute(() -> {
                        try {
                            mailRepository.deleteMailLocal(mailId);
                        } catch (Exception ex) {
                            Log.e(TAG, "deleteMail: local Room delete error: " + ex.getMessage(), ex);
                        }
                    });
                }
            }
        });
    }

    /* =========================
       Move (offline-first) — fixed server/local mapping
       ========================= */

    public void moveToLabelOfflineFirst(String mailId, String targetLabelRaw, String jwtToken, Context appContext, Runnable onSuccessUi) {
        String targetLabelLocal = normalizeLabelLocal(targetLabelRaw); // Room/UI
        String targetLabelServer = apiLabel(targetLabelLocal);         // REST

        if (targetLabelLocal == null || targetLabelLocal.isEmpty()) {
            errorMessage.postValue("Invalid target label");
            return;
        }

        // Update UI now for snappy UX (local = primary)
        tryUpdateMoveInUi(mailId, targetLabelLocal);

        // Always update local Room immediately
        ioExecutor.execute(() -> {
            try {
                String ownerId = getOwnerId(appContext);
                List<String> removedLocal = mailRepository.moveMailLocal(mailId, ownerId != null ? ownerId : "", targetLabelLocal);

                if (jwtToken == null || jwtToken.isEmpty()) {
                    // Offline: queue pending operation (stores local names)
                    enqueueMovePending(mailId, targetLabelLocal, removedLocal);
                    if (onSuccessUi != null) onSuccessUi.run();
                    return;
                }

                // Try online: remove old labels on server (convert each to server form)
                boolean allOk = true;
                if (removedLocal != null) {
                    for (String labelLocal : removedLocal) {
                        if ("starred".equalsIgnoreCase(labelLocal)) continue;
                        String labelServer = apiLabel(labelLocal);

                        JSONObject json = new JSONObject();
                        json.put("label", labelServer);
                        json.put("action", "remove");
                        RequestBody body = RequestBody.create(JSON, json.toString());
                        Request req = new Request.Builder()
                                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                                .patch(body)
                                .header("Authorization", "Bearer " + jwtToken)
                                .build();
                        try (Response resp = client.newCall(req).execute()) {
                            if (!resp.isSuccessful()) {
                                allOk = false;
                            }
                        }
                    }
                }

                // Add target label on server (server = inbox, local = primary)
                JSONObject addJson = new JSONObject();
                addJson.put("label", targetLabelServer);
                RequestBody addBody = RequestBody.create(JSON, addJson.toString());
                Request addReq = new Request.Builder()
                        .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                        .patch(addBody)
                        .header("Authorization", "Bearer " + jwtToken)
                        .build();
                try (Response addResp = client.newCall(addReq).execute()) {
                    if (!addResp.isSuccessful()) {
                        allOk = false;
                    }
                }

                if (!allOk) {
                    // Keep local state and queue a move to be retried later (local names)
                    enqueueMovePending(mailId, targetLabelLocal, removedLocal);
                }
                if (onSuccessUi != null) onSuccessUi.run();
            } catch (Exception e) {
                Log.e(TAG, "moveToLabelOfflineFirst error: " + e.getMessage(), e);
                // Keep local state; enqueue pending op as fallback
                enqueueMovePending(mailId, targetLabelLocal, null);
                if (onSuccessUi != null) onSuccessUi.run();
            }
        });
    }

    private void enqueueMovePending(String mailId, String targetLabelLocal, List<String> removedLocal) {
        try {
            pendingRepo.enqueueLabelMove(mailId, targetLabelLocal, removedLocal != null ? removedLocal : new ArrayList<>());
            Log.d(TAG, "enqueueMovePending: enqueued move op for mail=" + mailId + " target=" + targetLabelLocal);
        } catch (Exception e) {
            Log.e(TAG, "enqueueMovePending error: " + e.getMessage(), e);
        }
    }

    /* =========================
       UI helpers
       ========================= */

    private void tryUpdateLabelsInUi(String mailId, boolean add, String labelLocal) {
        try {
            JSONObject cur = mailData.getValue();
            if (cur == null) return;
            if (!mailId.equals(cur.optString("id"))) return;

            if ("starred".equalsIgnoreCase(labelLocal)) {
                cur.put("starred", add);
            }

            List<String> labels = MailMapper.labelIdsFromJson(cur);
            if (add) {
                if (!labels.contains(labelLocal)) labels.add(labelLocal);
            } else {
                List<String> newList = new ArrayList<>();
                for (String l : labels) if (!l.equalsIgnoreCase(labelLocal)) newList.add(l);
                labels = newList;
            }
            JSONArray newArr = new JSONArray();
            for (String l : labels) newArr.put(l);
            cur.put("labels", newArr);
            mailData.postValue(cur);
        } catch (Exception e) {
            Log.e(TAG, "tryUpdateLabelsInUi error: " + e.getMessage(), e);
        }
    }

    private void tryUpdateMoveInUi(String mailId, String targetLabelLocal) {
        try {
            JSONObject cur = mailData.getValue();
            if (cur == null) return;
            if (!mailId.equals(cur.optString("id"))) return;

            List<String> labels = MailMapper.labelIdsFromJson(cur);
            List<String> after = new ArrayList<>();
            for (String l : labels) {
                if ("starred".equalsIgnoreCase(l)) {
                    after.add(l);
                    continue;
                }
                if (!com.example.gmailish.data.repository.MailRepository.isInboxLabel(l)) {
                    after.add(l);
                }
            }
            if (!after.contains(targetLabelLocal)) after.add(targetLabelLocal);

            JSONArray newArr = new JSONArray();
            for (String l : after) newArr.put(l);
            cur.put("labels", newArr);
            mailData.postValue(cur);
        } catch (Exception e) {
            Log.e(TAG, "tryUpdateMoveInUi error: " + e.getMessage(), e);
        }
    }
}
