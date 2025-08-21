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

import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.db.AppDbProvider;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.repository.MailRepository;
import com.example.gmailish.model.Email;
import com.example.gmailish.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class InboxViewModel extends AndroidViewModel {

    private static final String TAG = "InboxVM";

    // Special keys
    private static final String KEY_ALL_INBOXES = "__ALL__";
    private static final String LABEL_INBOX     = "inbox";
    private static final String LABEL_PRIMARY   = "primary";
    private static final String LABEL_DRAFTS    = "drafts";
    private static final String LABEL_SENT      = "sent";
    private static final String LABEL_OUTBOX    = "outbox";

    // Buckets excluded from “All inboxes”
    private static final Set<String> EXCLUDED_LABELS = new HashSet<>(java.util.Arrays.asList(
            LABEL_SENT, LABEL_DRAFTS, LABEL_OUTBOX
    ));

    private final MutableLiveData<List<Email>> emailsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData       = new MutableLiveData<>();
    private final MutableLiveData<User> currentUserLiveData   = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> unreadCountsLiveData = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private final MailRepository mailRepo;

    public InboxViewModel(@NonNull Application application) {
        super(application);
        Log.d(TAG, "InboxViewModel: init");
        AppDatabase db = AppDbProvider.get(application.getApplicationContext());
        mailRepo = new MailRepository(db.mailDao(), db.labelDao(), db.mailLabelDao());
    }

    public LiveData<List<Email>> getEmails() { return emailsLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<User> getCurrentUserLiveData() { return currentUserLiveData; }
    public LiveData<Map<String, Integer>> getUnreadCounts() { return unreadCountsLiveData; }

    /* =========================
       Timestamp helpers
       ========================= */

    private String toIso8601(Date date) {
        if (date == null) return "";
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return date.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else {
            SimpleDateFormat fmt =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
            return fmt.format(date);
        }
    }

    private Date parseAnyTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        // 1) ISO-8601
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                return Date.from(java.time.OffsetDateTime.parse(raw).toInstant());
            } else {
                String n = normalizeIso(raw);
                SimpleDateFormat s =
                        new SimpleDateFormat(n.contains(".")
                                ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
                if (n.endsWith("Z")) s.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return s.parse(n);
            }
        } catch (Exception ignored) {}

        // 2) Epoch seconds/millis
        try {
            if (raw.matches("^\\d{10,13}$")) {
                long v = Long.parseLong(raw);
                if (v < 1_000_000_000_000L) v *= 1000L;
                return new Date(v);
            }
        } catch (Exception ignored) {}

        // 3) Java Date.toString()
        try {
            SimpleDateFormat s = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
            return s.parse(raw);
        } catch (Exception ignored) {}

        return null;
    }

    private String normalizeIso(String iso) {
        if (iso == null) return null;
        int p = Math.max(iso.lastIndexOf('+'), iso.lastIndexOf('-'));
        if (p > 10 && iso.length() >= p + 6 && iso.charAt(iso.length() - 3) == ':') {
            return iso.substring(0, iso.length() - 3) + iso.substring(iso.length() - 2);
        }
        return iso;
    }

    /* =========================
       Helpers
       ========================= */

    private boolean hasStarredLabel(JSONObject obj) {
        JSONArray labels = obj.optJSONArray("labels");
        if (labels == null) return false;
        for (int j = 0; j < labels.length(); j++) {
            if ("starred".equalsIgnoreCase(labels.optString(j))) return true;
        }
        return false;
    }

    private boolean hasDraftLabel(JSONObject obj) {
        JSONArray labels = obj.optJSONArray("labels");
        if (labels != null) {
            for (int j = 0; j < labels.length(); j++) {
                if ("drafts".equalsIgnoreCase(labels.optString(j))) return true;
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

    /** Local normalization: server "inbox" becomes local "primary" everywhere. */
    private String normalizeLabel(String label) {
        if (label == null) return null;
        if (LABEL_INBOX.equalsIgnoreCase(label)) return LABEL_PRIMARY;
        return label.toLowerCase(Locale.ROOT);
    }

    /** API mapping: when calling the server, convert local "primary" back to "inbox". */
    private String apiLabel(String label) {
        if (label == null) return null;
        return LABEL_PRIMARY.equalsIgnoreCase(label) ? LABEL_INBOX : label.toLowerCase(Locale.ROOT);
    }

    private String getJwtToken() {
        SharedPreferences prefs = getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        String jwt = prefs.getString("jwt", null);
        Log.d(TAG, "getJwtToken -> " + (jwt != null));
        return jwt;
    }

    private String resolveMe(String value, String currentUserId) {
        if (value == null || value.isEmpty()) return value;
        if ("me".equalsIgnoreCase(value) && currentUserId != null && !currentUserId.isEmpty()) {
            return currentUserId;
        }
        return value;
    }

    private List<Email> parseEmailList(JSONArray jsonArray) {
        try {
            List<Email> parsedEmails = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                boolean isStarred = hasStarredLabel(obj);
                boolean isDraft = hasDraftLabel(obj);

                parsedEmails.add(new Email(
                        obj.optString("senderName"),
                        obj.optString("subject"),
                        obj.optString("content"),
                        obj.optString("timestamp"),
                        obj.optBoolean("read"),
                        isStarred,
                        obj.optString("id"),
                        obj.optString("recipientEmail", null),
                        isDraft
                ));
            }
            return parsedEmails;
        } catch (Exception e) {
            Log.e(TAG, "parseEmailList error: " + e.getMessage());
            errorLiveData.postValue("JSON parse error: " + e.getMessage());
            return null;
        }
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

                    String id          = obj.optString("id");
                    String senderName  = obj.optString("senderName");
                    String subject     = obj.optString("subject");
                    String content     = obj.optString("content");
                    boolean read       = obj.optBoolean("read");
                    boolean starred    = hasStarredLabel(obj);

                    // Resolve identity fields including "me"
                    String senderId    = resolveMe(obj.optString("senderId", null), currentUserId);
                    String recipientId = resolveMe(obj.optString("recipientId", null), currentUserId);
                    String ownerId     = resolveMe(obj.optString("ownerId", null), currentUserId);

                    String recipientName  = obj.optString("recipientName", null);
                    String recipientEmail = obj.optString("recipientEmail", null);

                    // Parse timestamp (supports ISO/epoch/Date.toString)
                    Date ts = parseAnyTimestamp(obj.optString("timestamp", null));

                    MailEntity me = new MailEntity(
                            id,
                            senderId,
                            senderName,
                            recipientId,
                            recipientName,
                            recipientEmail,
                            subject,
                            content,
                            ts,   // <-- parsed Date instead of null
                            ownerId,
                            read,
                            starred
                    );
                    mails.add(me);

                    List<String> labels = new ArrayList<>();
                    for (String raw : parseLabelsArray(obj)) {
                        labels.add(normalizeLabel(raw)); // server -> local
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

    private List<Email> mapEntitiesToEmails(List<MailEntity> mails) {
        List<Email> mapped = new ArrayList<>();
        if (mails == null) return mapped;
        for (MailEntity m : mails) {
            mapped.add(new Email(
                    m.getSenderName(),
                    m.getSubject(),
                    m.getContent(),
                    toIso8601(m.getTimestamp()),   // <-- emit ISO, not Date.toString()
                    m.getRead(),
                    m.getStarred(),
                    m.getId(),
                    m.getRecipientEmail(),
                    m.isDraft()
            ));
        }
        return mapped;
    }

    /* =========================
       Unread counts
       ========================= */

    public void refreshUnreadCounts() {
        String token = getJwtToken();
        if (token == null) {
            errorLiveData.postValue("JWT token missing");
            return;
        }
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails")
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "refreshUnreadCounts network error: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "refreshUnreadCounts error code: " + r.code());
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "[]";
                    JSONArray arr = new JSONArray(body);

                    Map<String, Integer> counts = new HashMap<>();
                    int allInboxesUnread = 0;

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        boolean read = obj.optBoolean("read", false);
                        if (read) continue;

                        JSONArray labels = obj.optJSONArray("labels");

                        if (labels == null || labels.length() == 0) {
                            String key = LABEL_PRIMARY; // no labels -> primary
                            counts.put(key, counts.getOrDefault(key, 0) + 1);
                            allInboxesUnread += 1;
                            continue;
                        }

                        boolean excluded = false;
                        Set<String> perMailLabels = new HashSet<>();
                        for (int j = 0; j < labels.length(); j++) {
                            String lb = normalizeLabel(labels.optString(j, ""));
                            perMailLabels.add(lb);
                            if (EXCLUDED_LABELS.contains(lb)) excluded = true;
                        }

                        for (String lb : perMailLabels) {
                            counts.put(lb, counts.getOrDefault(lb, 0) + 1);
                        }
                        if (!excluded) {
                            allInboxesUnread += 1;
                        }
                    }

                    counts.put(KEY_ALL_INBOXES, allInboxesUnread);
                    unreadCountsLiveData.postValue(counts);
                } catch (Exception e) {
                    Log.e(TAG, "refreshUnreadCounts parse error: " + e.getMessage());
                }
            }
        });
    }

    /* =========================
       API calls
       ========================= */

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
                        String id        = json.optString("id", null);
                        String username  = json.optString("username", null);
                        String picture   = json.optString("picture", "");
                        String pictureUrl= json.optString("pictureUrl", null);

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
                Log.e(TAG, "Search network error: " + e.getMessage());
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
                } catch (Exception e) {
                    errorLiveData.postValue("JSON parse error: " + e.getMessage());
                }
            }
        });
    }

    public void loadEmailsByLabel(String label) {
        errorLiveData.setValue(null);
        String token = getJwtToken();
        String normalized = normalizeLabel(label); // local name (primary, starred, etc.)

        // Drafts are local-only
        if (LABEL_DRAFTS.equalsIgnoreCase(normalized)) {
            loadEmailsByLabelLocal(LABEL_DRAFTS);
            return;
        }

        Log.d(TAG, "loadEmailsByLabel (remote): raw=" + label + " normalized(local)=" + normalized);

        if (token == null) {
            errorLiveData.postValue("JWT token missing");
            return;
        }

        // Convert local "primary" back to server "inbox"
        String serverLabel = apiLabel(normalized);
        String url = "http://10.0.2.2:3000/api/mails?label=" + serverLabel;

        Request request = new Request.Builder().url(url).header("Authorization", "Bearer " + token).build();

        client.newCall(request).enqueue(new Callback() {

            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Label '" + label + "' network error: " + e.getMessage());
                errorLiveData.postValue("Failed to load '" + label + "': " + e.getMessage());
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
        Log.d(TAG, "loadEmailsByLabelLocal: raw=" + labelIdRaw + " normalized(local)=" + labelId);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SharedPreferences prefs = getApplication().getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String ownerId = prefs.getString("user_id", null);

                List<MailEntity> mails = mailRepo.getMailsForLabelLocal(labelId, ownerId);
                List<Email> mapped = mapEntitiesToEmails(mails);

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
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "loadAllInboxes network error: " + e.getMessage());
                errorLiveData.postValue("Network error: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "loadAllInboxes error: code=" + r.code());
                        errorLiveData.postValue("Error code: " + r.code());
                        return;
                    }

                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "AllInboxes raw: " + body);

                    List<Email> parsedEmails = new ArrayList<>();
                    JSONArray arr = new JSONArray(body);

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);

                        // Exclude system buckets from “All inboxes”
                        boolean exclude = false;
                        JSONArray labels = obj.optJSONArray("labels");
                        if (labels != null) {
                            for (int j = 0; j < labels.length(); j++) {
                                String lb = normalizeLabel(labels.optString(j, ""));
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

                    // Sort newest-first (ISO strings compare lexicographically)
                    parsedEmails.sort((a, b) -> {
                        String ta = a.timestamp, tb = b.timestamp;
                        if (ta == null) ta = "";
                        if (tb == null) tb = "";
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

                    // Keep local cache in sync for offline
                    try {
                        syncToLocal(arr);
                    } catch (Throwable t) {
                        Log.w(TAG, "syncToLocal failed (non-fatal): " + t.getMessage());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "AllInboxes JSON parse error: " + e.getMessage());
                    errorLiveData.postValue("JSON parse error: " + e.getMessage());
                }
            }
        });
    }
}
