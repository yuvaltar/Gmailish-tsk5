package com.example.gmailish.mail;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.repository.MailRepository;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MailViewModel extends ViewModel {


    private static final String TAG = "MailVM";

    public MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public MutableLiveData<JSONObject> mailData = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Room repo and background executor for DB writes (called after server success)
    private MailRepository mailRepository; // initialized via init(context)
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private boolean initialized = false;
    private String cachedOwnerId;

    // Call this once from Activity.onCreate to supply Application context for Room
    public void init(Context appContext) {
        if (initialized) {
            Log.d(TAG, "init: already initialized");
            return;
        }
        Log.d(TAG, "init: creating AppDatabase and MailRepository");
        AppDatabase db = AppDatabase.getInstance(appContext.getApplicationContext());
        mailRepository = new MailRepository(db.mailDao(), db.labelDao(), db.mailLabelDao());
        initialized = true;
        Log.d(TAG, "init: initialized=true");
    }

    private String normalizeLabel(String label) {
        return label.equalsIgnoreCase("inbox") ? "primary" : label;
    }

    private String getOwnerId(Context ctx) {
        if (cachedOwnerId != null) return cachedOwnerId;
        SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        cachedOwnerId = prefs.getString("user_id", null);
        Log.d(TAG, "getOwnerId -> " + cachedOwnerId);
        return cachedOwnerId;
    }

    public void fetchMailById(String mailId, String jwtToken) {
        Log.d(TAG, "fetchMailById: mailId=" + mailId + " hasToken=" + (jwtToken != null));
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
                    JSONObject mail = new JSONObject(body);
                    mailData.postValue(mail);
                } catch (Exception e) {
                    Log.e(TAG, "fetchMailById parse error: " + e.getMessage());
                    errorMessage.postValue("Parsing error");
                }
            }
        });
    }

    public void toggleStar(String mailId, String jwtToken) {
        Log.d(TAG, "toggleStar: mailId=" + mailId);
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/star")
                .patch(RequestBody.create("", null))
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "toggleStar network failure: " + e.getMessage());
                errorMessage.postValue("Star toggle failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) {
                Log.d(TAG, "toggleStar response code=" + response.code());
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Failed to toggle star: " + response.code());
                }
            }
        });
    }

    // pass appContext so we can update Room after server success
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
                try {
                    String respBody = response.body() != null ? response.body().string() : "";
                    Log.v(TAG, "deleteMail server body: " + respBody);
                } catch (Exception ignored) {}

                if (!response.isSuccessful()) {
                    errorMessage.postValue("Delete failed: " + code);
                    return;
                }

                // Server success -> delete locally in Room
                ioExecutor.execute(() -> {
                    try {
                        Log.d(TAG, "deleteMail: executing local delete in executor. initialized=" + initialized);
                        if (!initialized) {
                            Log.d(TAG, "deleteMail: init repository lazily in executor");
                            init(appContext.getApplicationContext()); // ensure repo
                        }
                        Log.d(TAG, "deleteMail: calling mailRepository.deleteMailBlocking for " + mailId);
                        mailRepository.deleteMailBlocking(mailId); // clears cross-refs then deletes
                        Log.d(TAG, "deleteMail: local Room delete completed for " + mailId);
                    } catch (Exception ex) {
                        Log.e(TAG, "deleteMail: local Room delete error: " + ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    // after server success, update Room relation so label lists reflect it
    public void addLabel(String mailId, String label, String jwtToken, Context appContext) {
        label = normalizeLabel(label);
        Log.d(TAG, "addLabel: mailId=" + mailId + " label=" + label);
        JSONObject json = new JSONObject();
        try {
            json.put("label", label);
        } catch (Exception e) {
            Log.e(TAG, "addLabel JSON build error: " + e.getMessage());
            errorMessage.postValue("Add label JSON error");
            return;
        }

        RequestBody body = RequestBody.create(JSON, json.toString());

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        final String labelId = label; // adjust if backend uses different ids

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

                // Server success -> update Room cross-ref
                ioExecutor.execute(() -> {
                    try {
                        Log.d(TAG, "addLabel: executing local add in executor. initialized=" + initialized);
                        if (!initialized) init(appContext.getApplicationContext());
                        String ownerId = getOwnerId(appContext);
                        Log.d(TAG, "addLabel: ownerId=" + ownerId + " upserting label and cross-ref");
                        mailRepository.addLabelToMailBlocking(
                                mailId,
                                labelId,
                                ownerId != null ? ownerId : "",
                                labelId
                        );
                        Log.d(TAG, "addLabel: local Room relation added for mail=" + mailId + " label=" + labelId);
                    } catch (Exception ex) {
                        Log.e(TAG, "addLabel: local Room add error: " + ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    // after server success, update Room relation so removal reflects in label lists
    public void removeLabel(String mailId, String label, String jwtToken, Context appContext) {
        label = normalizeLabel(label);
        Log.d(TAG, "removeLabel: mailId=" + mailId + " label=" + label);
        JSONObject json = new JSONObject();
        try {
            json.put("label", label);
            json.put("action", "remove");
        } catch (Exception e) {
            Log.e(TAG, "removeLabel JSON build error: " + e.getMessage());
            errorMessage.postValue("Remove label JSON error");
            return;
        }

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

                // Server success -> update Room cross-ref
                ioExecutor.execute(() -> {
                    try {
                        Log.d(TAG, "removeLabel: executing local removal in executor. initialized=" + initialized);
                        if (!initialized) init(appContext.getApplicationContext());
                        mailRepository.removeLabelFromMailBlocking(mailId, labelId);
                        Log.d(TAG, "removeLabel: local Room relation removed for mail=" + mailId + " label=" + labelId);
                    } catch (Exception ex) {
                        Log.e(TAG, "removeLabel: local Room remove error: " + ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    public void addOrRemoveLabel(String mailId, String label, String jwtToken, boolean shouldAdd, Context appContext) {
        Log.d(TAG, "addOrRemoveLabel: mailId=" + mailId + " label=" + label + " shouldAdd=" + shouldAdd);
        if (shouldAdd) {
            addLabel(mailId, label, jwtToken, appContext);
        } else {
            removeLabel(mailId, label, jwtToken, appContext);
        }
    }

    // Keep this for flows that need a callback sequence in the Activity
    public void removeLabelWithCallback(String mailId, String label, String jwtToken, Runnable onSuccess) {
        label = normalizeLabel(label);
        Log.d(TAG, "removeLabelWithCallback: mailId=" + mailId + " label=" + label);
        JSONObject json = new JSONObject();
        try {
            json.put("label", label);
            json.put("action", "remove");
        } catch (Exception e) {
            Log.e(TAG, "removeLabelWithCallback JSON build error: " + e.getMessage());
            errorMessage.postValue("Remove label JSON error");
            return;
        }

        RequestBody body = RequestBody.create(JSON, json.toString());

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "removeLabelWithCallback network failure: " + e.getMessage());
                errorMessage.postValue("Remove label failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "removeLabelWithCallback server response code=" + response.code());
                if (response.isSuccessful()) {
                    onSuccess.run();
                } else {
                    errorMessage.postValue("Remove label failed: " + response.code());
                }
            }
        });
    }
}