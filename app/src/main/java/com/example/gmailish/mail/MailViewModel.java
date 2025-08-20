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
    private final PendingOperationRepository pendingRepo; // NEW

    @Inject
    public MailViewModel(MailRepository mailRepository, PendingOperationRepository pendingRepo) { // CHANGED
        this.mailRepository = mailRepository;
        this.pendingRepo = pendingRepo; // NEW
    }

    private String normalizeLabel(String label) {
        return label != null && label.equalsIgnoreCase("inbox") ? "primary" : (label != null ? label.toLowerCase() : null);
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

    // OFFLINE-FIRST detail load
    public void loadMailDetail(Context appContext, String mailId, String jwtToken) {
        // 1) Try local immediately
        ioExecutor.execute(() -> {
            try {
                MailEntity local = mailRepository.getByIdSync(mailId);
                if (local != null) {
                    List<String> labels = mailRepository.getLabelsForMailSync(mailId);
                    JSONObject localJson = mailRepository.buildMailJson(local, labels);
                    if (localJson != null) {
                        mailData.postValue(localJson);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadMailDetail local error: " + e.getMessage(), e);
            }
        });

        // 2) Then refresh from network (if token present)
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
                Log.d(TAG, "fetchMailById response code=" + response.code());
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Error: " + response.code());
                    return;
                }
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.v(TAG, "fetchMailById body: " + body);
                    JSONObject mailJson = new JSONObject(body);
                    // Post to UI
                    mailData.postValue(mailJson);
                    // Cache to Room
                    ioExecutor.execute(() -> {
                        try {
                            MailEntity entity = MailMapper.mailEntityFromJson(mailJson);
                            List<String> labels = MailMapper.labelIdsFromJson(mailJson);
                            // upsert mail
                            mailRepository.saveMail(entity);
                            // replace label links
                            String ownerId = entity.getOwnerId();
                            mailRepository.replaceMailLabels(entity.getId(), labels, ownerId != null ? ownerId : "");
                        } catch (Exception ex) {
                            Log.e(TAG, "caching mail error: " + ex.getMessage(), ex);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "fetchMailById parse error: " + e.getMessage());
                    errorMessage.postValue("Parsing error");
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
                Log.d(TAG, "markAsRead response code=" + response.code());
                if (!response.isSuccessful()) {
                    Log.e(TAG, "markAsRead failed: " + response.code());
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
        });
    }

    public void addLabel(String mailId, String label, String jwtToken, Context appContext) {
        label = normalizeLabel(label);
        Log.d(TAG, "addLabel: mailId=" + mailId + " label=" + label);
        JSONObject json = new JSONObject();
        try { json.put("label", label); }
        catch (Exception e) { Log.e(TAG, "addLabel JSON build error: " + e.getMessage()); errorMessage.postValue("Add label JSON error"); return; }
        RequestBody body = RequestBody.create(JSON, json.toString());
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        final String labelId = label;
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "addLabel network failure: " + e.getMessage());
                errorMessage.postValue("Add label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "addLabel server response code=" + response.code());
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Add label failed: " + response.code());
                    return;
                }
                ioExecutor.execute(() -> {
                    try {
                        String ownerId = getOwnerId(appContext);
                        Log.d(TAG, "addLabel: ownerId=" + ownerId + " upserting label and cross-ref");
                        mailRepository.addLabelToMailLocal(mailId, labelId, ownerId != null ? ownerId : "", labelId);
                        // update UI LiveData labels
                        tryUpdateLabelsInUi(mailId, true, labelId);
                    } catch (Exception ex) {
                        Log.e(TAG, "addLabel: local Room add error: " + ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    public void removeLabel(String mailId, String label, String jwtToken, Context appContext) {
        label = normalizeLabel(label);
        Log.d(TAG, "removeLabel: mailId=" + mailId + " label=" + label);
        JSONObject json = new JSONObject();
        try { json.put("label", label); json.put("action", "remove"); }
        catch (Exception e) { Log.e(TAG, "removeLabel JSON build error: " + e.getMessage()); errorMessage.postValue("Remove label JSON error"); return; }
        RequestBody body = RequestBody.create(JSON, json.toString());
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();
        final String labelId = label;
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "removeLabel network failure: " + e.getMessage());
                errorMessage.postValue("Remove label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "removeLabel server response code=" + response.code());
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Remove label failed: " + response.code());
                    return;
                }
                ioExecutor.execute(() -> {
                    try {
                        mailRepository.removeLabelFromMailLocal(mailId, labelId);
                        tryUpdateLabelsInUi(mailId, false, labelId);
                    } catch (Exception ex) {
                        Log.e(TAG, "removeLabel: local Room remove error: " + ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    public void addOrRemoveLabel(String mailId, String label, String jwtToken, boolean shouldAdd, Context appContext) {
        Log.d(TAG, "addOrRemoveLabel: mailId=" + mailId + " label=" + label + " shouldAdd=" + shouldAdd);
        if (shouldAdd) addLabel(mailId, label, jwtToken, appContext);
        else removeLabel(mailId, label, jwtToken, appContext);
    }

    public void removeLabelWithCallback(String mailId, String label, String jwtToken, Runnable onSuccess) {
        label = normalizeLabel(label);
        Log.d(TAG, "removeLabelWithCallback: mailId=" + mailId + " label=" + label);
        JSONObject json = new JSONObject();
        try { json.put("label", label); json.put("action", "remove"); }
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
                Log.d(TAG, "removeLabelWithCallback server response code=" + response.code());
                if (response.isSuccessful()) onSuccess.run();
                else errorMessage.postValue("Remove label failed: " + response.code());
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
                int code = response.code();
                Log.d(TAG, "deleteMail server response code=" + code);
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Delete failed: " + code);
                    return;
                }
                ioExecutor.execute(() -> {
                    try {
                        Log.d(TAG, "deleteMail: calling mailRepository.deleteMailLocal for " + mailId);
                        mailRepository.deleteMailLocal(mailId);
                        Log.d(TAG, "deleteMail: local Room delete completed for " + mailId);
                    } catch (Exception ex) {
                        Log.e(TAG, "deleteMail: local Room delete error: " + ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    // Move to label with offline-first + pending fallback
    public void moveToLabelOfflineFirst(String mailId, String targetLabelRaw, String jwtToken, Context appContext, Runnable onSuccessUi) {
        String targetLabel = normalizeLabel(targetLabelRaw);
        if (targetLabel == null || targetLabel.isEmpty()) {
            errorMessage.postValue("Invalid target label");
            return;
        }

        // Update UI now for snappy UX
        tryUpdateMoveInUi(mailId, targetLabel);

        // Always update local Room immediately
        ioExecutor.execute(() -> {
            try {
                String ownerId = getOwnerId(appContext);
                List<String> removed = mailRepository.moveMailLocal(mailId, ownerId != null ? ownerId : "", targetLabel);

                if (jwtToken == null || jwtToken.isEmpty()) {
                    // Offline: queue pending operation
                    enqueueMovePending(mailId, targetLabel, removed);
                    if (onSuccessUi != null) onSuccessUi.run();
                    return;
                }

                // Try online server move: remove then add
                boolean allOk = true;
                for (String label : removed) {
                    if ("starred".equalsIgnoreCase(label)) continue;
                    JSONObject json = new JSONObject();
                    json.put("label", label);
                    json.put("action", "remove");
                    RequestBody body = RequestBody.create(JSON, json.toString());
                    Request req = new Request.Builder()
                            .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                            .patch(body)
                            .header("Authorization", "Bearer " + jwtToken)
                            .build();
                    Response resp = client.newCall(req).execute();
                    if (!resp.isSuccessful()) {
                        allOk = false;
                    }
                    if (resp.body() != null) resp.close();
                }

                JSONObject addJson = new JSONObject();
                addJson.put("label", targetLabel);
                RequestBody addBody = RequestBody.create(JSON, addJson.toString());
                Request addReq = new Request.Builder()
                        .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                        .patch(addBody)
                        .header("Authorization", "Bearer " + jwtToken)
                        .build();
                Response addResp = client.newCall(addReq).execute();
                if (!addResp.isSuccessful()) {
                    allOk = false;
                }
                if (addResp.body() != null) addResp.close();

                if (!allOk) {
                    // Keep local state and queue a move to be retried later
                    enqueueMovePending(mailId, targetLabel, removed);
                }
                if (onSuccessUi != null) onSuccessUi.run();
            } catch (Exception e) {
                Log.e(TAG, "moveToLabelOfflineFirst error: " + e.getMessage(), e);
                // Keep local state; enqueue pending op as fallback
                enqueueMovePending(mailId, targetLabel, null);
                if (onSuccessUi != null) onSuccessUi.run();
            }
        });
    }

    private void enqueueMovePending(String mailId, String targetLabel, List<String> removed) {
        try {
            pendingRepo.enqueueLabelMove(mailId, targetLabel, removed != null ? removed : new ArrayList<>());
            Log.d(TAG, "enqueueMovePending: enqueued move op for mail=" + mailId + " target=" + targetLabel);
        } catch (Exception e) {
            Log.e(TAG, "enqueueMovePending error: " + e.getMessage(), e);
        }
    }

    private void tryUpdateLabelsInUi(String mailId, boolean add, String labelId) {
        try {
            JSONObject cur = mailData.getValue();
            if (cur == null) return;
            if (!mailId.equals(cur.optString("id"))) return;
            if ("starred".equalsIgnoreCase(labelId)) {
                cur.put("starred", add);
            }
            List<String> labels = MailMapper.labelIdsFromJson(cur);
            if (add) {
                if (!labels.contains(labelId)) labels.add(labelId);
            } else {
                List<String> newList = new ArrayList<>();
                for (String l : labels) if (!l.equalsIgnoreCase(labelId)) newList.add(l);
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

    private void tryUpdateMoveInUi(String mailId, String targetLabel) {
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
            if (!after.contains(targetLabel)) after.add(targetLabel);

            JSONArray newArr = new JSONArray();
            for (String l : after) newArr.put(l);
            cur.put("labels", newArr);
            mailData.postValue(cur);
        } catch (Exception e) {
            Log.e(TAG, "tryUpdateMoveInUi error: " + e.getMessage(), e);
        }
    }
}
