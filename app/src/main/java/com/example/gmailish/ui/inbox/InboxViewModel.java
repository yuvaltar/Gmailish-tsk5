package com.example.gmailish.ui.inbox;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.repository.MailRepository;
import com.example.gmailish.model.Email;
import com.example.gmailish.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class InboxViewModel extends AndroidViewModel {


    private static final String TAG = "InboxVM";

    private final MutableLiveData<List<Email>> emailsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<User> currentUserLiveData = new MutableLiveData<>();
    private final OkHttpClient client = new OkHttpClient();

    private final MailRepository mailRepo;

    public InboxViewModel(@NonNull Application application) {
        super(application);
        Log.d(TAG, "InboxViewModel: init");
        AppDatabase db = Room.databaseBuilder(
                application.getApplicationContext(),
                AppDatabase.class,
                "gmailish.db"
        ).fallbackToDestructiveMigration().build();
        mailRepo = new MailRepository(db.mailDao(), db.labelDao(), db.mailLabelDao());
    }

    public LiveData<List<Email>> getEmails() { return emailsLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<User> getCurrentUserLiveData() { return currentUserLiveData; }

    private boolean hasStarredLabel(JSONObject obj) {
        JSONArray labels = obj.optJSONArray("labels");
        if (labels != null) {
            for (int j = 0; j < labels.length(); j++) {
                if ("starred".equalsIgnoreCase(labels.optString(j))) return true;
            }
        }
        return false;
    }

    private List<String> parseLabelsArray(JSONObject obj) {
        List<String> out = new ArrayList<>();
        JSONArray labels = obj.optJSONArray("labels");
        if (labels != null) {
            for (int j = 0; j < labels.length(); j++) {
                String v = labels.optString(j);
                if (v != null && !v.isEmpty()) out.add(v);
            }
        }
        return out;
    }

    private String normalizeLabel(String label) {
        if (label == null) return null;
        if ("inbox".equalsIgnoreCase(label)) return "primary";
        return label.toLowerCase();
    }

    private String getJwtToken() {
        SharedPreferences prefs = getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        String jwt = prefs.getString("jwt", null);
        Log.d(TAG, "getJwtToken -> " + (jwt != null));
        return jwt;
    }

    private List<Email> parseEmailList(JSONArray jsonArray) {
        try {
            List<Email> parsedEmails = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                boolean isStarred = hasStarredLabel(obj);
                parsedEmails.add(new Email(
                        obj.optString("senderName"),
                        obj.optString("subject"),
                        obj.optString("content"),
                        obj.optString("timestamp"),
                        obj.optBoolean("read"),
                        isStarred,
                        obj.optString("id")
                ));
            }
            return parsedEmails;
        } catch (Exception e) {
            Log.e(TAG, "parseEmailList error: " + e.getMessage());
            errorLiveData.postValue("JSON parse error: " + e.getMessage());
            return null;
        }
    }

    private String resolveMe(String value, String currentUserId) {
        if (value == null || value.isEmpty()) return value;
        if ("me".equalsIgnoreCase(value) && currentUserId != null && !currentUserId.isEmpty()) {
            return currentUserId;
        }
        return value;
    }

    private void syncToLocal(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0) {
            Log.d(TAG, "syncToLocal: nothing to sync");
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SharedPreferences prefs = getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String currentUserId = prefs.getString("user_id", null);


                List<MailEntity> mails = new ArrayList<>();
                Map<String, List<String>> mailToLabels = new HashMap<>();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);

                    String id = obj.optString("id");
                    String senderName = obj.optString("senderName");
                    String subject = obj.optString("subject");
                    String content = obj.optString("content");
                    boolean read = obj.optBoolean("read");
                    boolean starred = hasStarredLabel(obj);

                    // Resolve identity fields including "me"
                    String senderId = resolveMe(obj.optString("senderId", null), currentUserId);
                    String recipientId = resolveMe(obj.optString("recipientId", null), currentUserId);
                    String ownerId = resolveMe(obj.optString("ownerId", null), currentUserId);

                    String recipientName = obj.optString("recipientName", null);
                    String recipientEmail = obj.optString("recipientEmail", null);

                    MailEntity me = new MailEntity(
                            id,
                            senderId,
                            senderName,
                            recipientId,
                            recipientName,
                            recipientEmail,
                            subject,
                            content,
                            null, // keep null or parse if you prefer
                            ownerId,
                            read,
                            starred
                    );
                    mails.add(me);

                    List<String> labels = new ArrayList<>();
                    for (String raw : parseLabelsArray(obj)) {
                        labels.add(normalizeLabel(raw));
                    }
                    mailToLabels.put(id, labels);
                }

                int crossRefs = mailRepo.saveMailsAndLabels(mails, mailToLabels);
                Log.d(TAG, "syncToLocal: mails=" + mails.size() + " crossRefs=" + crossRefs);
            } catch (Throwable t) {
                Log.e(TAG, "syncToLocal error: " + t.getMessage(), t);
            }
        });
    }

    public void loadCurrentUser(String token) {
        Log.d(TAG, "loadCurrentUser called. hasToken=" + (token != null));
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/users/me")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "CurrentUser network failure: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "CurrentUser response error: code=" + r.code());
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "CurrentUser raw: " + body);
                    try {
                        JSONObject json = new JSONObject(body);
                        String id = json.optString("id", null);
                        String username = json.optString("username", null);
                        String picture = json.optString("picture", "");
                        String pictureUrl = json.optString("pictureUrl", null);

                        SharedPreferences prefs = getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE);
                        prefs.edit().putString("user_id", id).putString("username", username).apply();

                        currentUserLiveData.postValue(new User(id, username, picture, pictureUrl));
                    } catch (Exception e) {
                        Log.e(TAG, "CurrentUser parse error: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void loadEmails(String jwtToken) {
        Log.d(TAG, "loadEmails called. hasToken=" + (jwtToken != null));
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails")
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "loadEmails network error: " + e.getMessage());
                errorLiveData.postValue("Network error: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        errorLiveData.postValue("Error code: " + r.code());
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "[]";
                    JSONArray array = new JSONArray(body);

                    List<Email> parsed = parseEmailList(array);
                    if (parsed != null) emailsLiveData.postValue(parsed);

                    syncToLocal(array);
                } catch (Exception e) {
                    Log.e(TAG, "loadEmails parse error: " + e.getMessage());
                    errorLiveData.postValue("Parse error");
                }
            }
        });
    }

    public void searchEmails(String query) {
        errorLiveData.setValue(null);
        String token = getJwtToken();
        Log.d(TAG, "searchEmails: q=" + query + " hasToken=" + (token != null));

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/search/" + query)
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                errorLiveData.postValue("Search error: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        errorLiveData.postValue("Search failed: " + r.code());
                        return;
                    }
                    String jsonStr = r.body() != null ? r.body().string() : "[]";
                    JSONArray array = new JSONArray(jsonStr);
                    List<Email> parsedEmails = parseEmailList(array);
                    if (parsedEmails != null) emailsLiveData.postValue(parsedEmails);
                    // optional: syncToLocal(array);
                } catch (Exception e) {
                    errorLiveData.postValue("JSON parse error: " + e.getMessage());
                }
            }
        });
    }

    public void loadEmailsByLabel(String label) {
        errorLiveData.setValue(null);
        String token = getJwtToken();
        String normalized = normalizeLabel(label);
        Log.d(TAG, "loadEmailsByLabel (remote): raw=" + label + " normalized=" + normalized);

        if (token == null) {
            errorLiveData.postValue("JWT token missing");
            return;
        }

        String url = "http://10.0.2.2:3000/api/mails?label=" + normalized;
        Request request = new Request.Builder().url(url).header("Authorization", "Bearer " + token).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                errorLiveData.postValue("Failed to load '" + normalized + "': " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        errorLiveData.postValue("Server error " + r.code() + " loading '" + normalized + "'");
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "[]";
                    JSONArray array = new JSONArray(body);
                    List<Email> parsed = parseEmailList(array);
                    if (parsed != null) emailsLiveData.postValue(parsed);
                    syncToLocal(array);
                } catch (Exception e) {
                    errorLiveData.postValue("Parse error: " + e.getMessage());
                }
            }
        });
    }

    public void loadEmailsByLabelLocal(String labelIdRaw) {
        errorLiveData.setValue(null);
        String labelId = normalizeLabel(labelIdRaw);
        Log.d(TAG, "loadEmailsByLabelLocal: raw=" + labelIdRaw + " normalized=" + labelId);


        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SharedPreferences prefs = getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String ownerId = prefs.getString("user_id", null);

                List<MailEntity> mails = mailRepo.getMailsForLabelLocal(labelId, ownerId);
                List<Email> mapped = new ArrayList<>();
                for (MailEntity m : mails) {
                    mapped.add(new Email(
                            m.getSenderName(),
                            m.getSubject(),
                            m.getContent(),
                            m.getTimestamp() != null ? m.getTimestamp().toString() : "",
                            m.getRead(),
                            m.getStarred(),
                            m.getId()
                    ));
                }
                Log.d(TAG, "loadEmailsByLabelLocal: mapped=" + mapped.size());
                emailsLiveData.postValue(mapped);
            } catch (Exception e) {
                Log.e(TAG, "loadEmailsByLabelLocal error: " + e.getMessage());
                errorLiveData.postValue("Failed to load local mails for '" + labelIdRaw + "'");
            }
        });
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        boolean online = caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        Log.d(TAG, "isOnline -> " + online);
        return online;
    }
}