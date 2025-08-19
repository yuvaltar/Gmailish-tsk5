package com.example.gmailish.ui.inbox;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.gmailish.model.Email;
import com.example.gmailish.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class InboxViewModel extends AndroidViewModel {

    private static final String TAG = "InboxVM";

    private final MutableLiveData<List<Email>> emailsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final OkHttpClient client = new OkHttpClient();
    private final MutableLiveData<User> currentUserLiveData = new MutableLiveData<>();

    private static final java.util.Set<String> EXCLUDED_LABELS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "sent", "drafts"
            ));

    public InboxViewModel(Application application) {
        super(application);
    }

    public LiveData<List<Email>> getEmails() {
        return emailsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<User> getCurrentUserLiveData() {
        return currentUserLiveData;
    }

    // ---- Helpers ----

    private boolean hasStarredLabel(JSONObject obj) {
        JSONArray labels = obj.optJSONArray("labels");
        if (labels != null) {
            for (int j = 0; j < labels.length(); j++) {
                if ("starred".equalsIgnoreCase(labels.optString(j))) return true;
            }
        }
        return false;
    }

    // ---- API calls ----

    // Fetch /users/me and save id/username into SharedPreferences for ComposeActivity
    public void loadCurrentUser(String token) {
        Log.d(TAG, "loadCurrentUser called. hasToken=" + (token != null));

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/users/me")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "CurrentUser network failure: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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

                        Log.d(TAG, "Parsed user -> id=" + id + ", username=" + username);

                        SharedPreferences prefs = getApplication()
                                .getSharedPreferences("prefs", Context.MODE_PRIVATE);
                        prefs.edit()
                                .putString("user_id", id)
                                .putString("username", username)
                                .apply();
                        Log.d(TAG, "Saved to prefs: user_id=" + id + ", username=" + username);

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
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "loadEmails network error: " + e.getMessage());
                errorLiveData.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "loadEmails error code: " + r.code());
                        errorLiveData.postValue("Error code: " + r.code());
                        return;
                    }
                    List<Email> parsed = parseEmailList(r);
                    Log.d(TAG, "loadEmails parsed count=" + (parsed != null ? parsed.size() : -1));
                    if (parsed != null) emailsLiveData.postValue(parsed);
                }
            }
        });
    }

    public void searchEmails(String query) {
        errorLiveData.setValue(null);
        String token = getJwtToken();
        Log.d(TAG, "searchEmails q=\"" + query + "\" hasToken=" + (token != null));

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/search/" + query)
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Search network error: " + e.getMessage());
                errorLiveData.postValue("Search error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "Search failed: code=" + r.code());
                        errorLiveData.postValue("Search failed: " + r.code());
                        return;
                    }
                    String jsonStr = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "Search raw: " + jsonStr);

                    try {
                        JSONArray jsonArray = new JSONArray(jsonStr);
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
                                    isStarred, // derive from labels
                                    obj.optString("id")
                            ));
                        }
                        Log.d(TAG, "Search parsed count=" + parsedEmails.size());
                        emailsLiveData.postValue(parsedEmails);
                    } catch (Exception e) {
                        Log.e(TAG, "Search JSON parse error: " + e.getMessage());
                        errorLiveData.postValue("JSON parse error: " + e.getMessage());
                    }
                }
            }
        });
    }

    private List<Email> parseEmailList(Response response) throws IOException {
        try {
            String body = response.body() != null ? response.body().string() : "[]";
            Log.d(TAG, "Emails raw: " + body);

            List<Email> parsedEmails = new ArrayList<>();
            JSONArray jsonArray = new JSONArray(body);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);

                boolean isStarred = hasStarredLabel(obj);

                parsedEmails.add(new Email(
                        obj.optString("senderName"),
                        obj.optString("subject"),
                        obj.optString("content"),
                        obj.optString("timestamp"),
                        obj.optBoolean("read"),
                        isStarred, // derive from labels
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

    private String getJwtToken() {
        SharedPreferences prefs = getApplication()
                .getSharedPreferences("prefs", Context.MODE_PRIVATE);
        String jwt = prefs.getString("jwt", null);
        Log.d(TAG, "getJwtToken -> " + (jwt != null));
        return jwt;
    }

    public void loadEmailsByLabel(String label) {
        errorLiveData.setValue(null);
        String token = getJwtToken();
        Log.d(TAG, "loadEmailsByLabel \"" + label + "\" hasToken=" + (token != null));

        if (token == null) {
            errorLiveData.postValue("JWT token missing");
            return;
        }

        String url = "http://10.0.2.2:3000/api/mails?label=" + label;

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Label '" + label + "' network error: " + e.getMessage());
                errorLiveData.postValue("Failed to load '" + label + "': " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "Label '" + label + "' server error: " + r.code());
                        errorLiveData.postValue("Server error " + r.code() + " loading '" + label + "'");
                        return;
                    }
                    List<Email> parsed = parseEmailList(r);
                    Log.d(TAG, "Label '" + label + "' parsed count=" + (parsed != null ? parsed.size() : -1));
                    if (parsed != null) emailsLiveData.postValue(parsed);
                }
            }
        });
    }

    public void loadAllInboxes() {
        errorLiveData.setValue(null);
        String token = getJwtToken();
        Log.d(TAG, "loadAllInboxes hasToken=" + (token != null));

        if (token == null) {
            errorLiveData.postValue("JWT token missing");
            return;
        }

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails") // no label filter
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "loadAllInboxes network error: " + e.getMessage());
                errorLiveData.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "loadAllInboxes error: code=" + r.code());
                        errorLiveData.postValue("Error code: " + r.code());
                        return;
                    }

                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "AllInboxes raw: " + body);

                    List<Email> parsedEmails = new ArrayList<>();
                    try {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);

                            // Check labels and exclude system buckets
                            boolean exclude = false;
                            JSONArray labels = obj.optJSONArray("labels");
                            if (labels != null) {
                                for (int j = 0; j < labels.length(); j++) {
                                    String lb = labels.optString(j, "").toLowerCase();
                                    if (EXCLUDED_LABELS.contains(lb)) {
                                        exclude = true;
                                        break;
                                    }
                                }
                            }
                            if (exclude) continue;

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

                        // Sort newest first. If your timestamp is millis, parse to long; if ISO-8601 strings, string compare often works.
                        parsedEmails.sort((a, b) -> {
                            String ta = a.timestamp, tb = b.timestamp;
                            if (ta == null) ta = "";
                            if (tb == null) tb = "";
                            // try numeric (millis) first
                            try {
                                long la = Long.parseLong(ta);
                                long lb = Long.parseLong(tb);
                                return Long.compare(lb, la);
                            } catch (NumberFormatException ignore) {
                                return tb.compareTo(ta);
                            }
                        });

                        Log.d(TAG, "AllInboxes parsed count=" + parsedEmails.size());
                        emailsLiveData.postValue(parsedEmails);

                    } catch (Exception e) {
                        Log.e(TAG, "AllInboxes JSON parse error: " + e.getMessage());
                        errorLiveData.postValue("JSON parse error: " + e.getMessage());
                    }
                }
            }
        });
    }
}
