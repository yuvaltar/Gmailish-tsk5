package com.example.gmailish.mail;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MailViewModel extends ViewModel {

    public MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public MutableLiveData<JSONObject> mailData = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private List<JSONObject> userLabels = new ArrayList<>(); // 🔹 stores user-defined labels

    private String normalizeLabel(String label) {
        return label.equalsIgnoreCase("inbox") ? "primary" : label;
    }

    public void setUserLabels(List<JSONObject> labels) {
        this.userLabels = labels;
    }

    public List<String> getUserLabelNames() {
        List<String> names = new ArrayList<>();
        for (JSONObject label : userLabels) {
            names.add(label.optString("name"));
        }
        return names;
    }

    public void fetchMailById(String mailId, String jwtToken) {
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId)
                .get()
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                errorMessage.postValue("Failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Error: " + response.code());
                    return;
                }

                try {
                    String body = response.body().string();
                    JSONObject mail = new JSONObject(body);
                    mailData.postValue(mail);
                } catch (Exception e) {
                    errorMessage.postValue("Parsing error");
                }
            }
        });
    }

    public void toggleStar(String mailId, String jwtToken) {
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/star")
                .patch(RequestBody.create("", null))
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                errorMessage.postValue("Star toggle failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Failed to toggle star: " + response.code());
                }
            }
        });
    }

    public void deleteMail(String mailId, String jwtToken) {
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId)
                .delete()
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                errorMessage.postValue("Delete failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Delete failed: " + response.code());
                }
            }
        });
    }

    public void addLabel(String mailId, String label, String jwtToken) {
        label = normalizeLabel(label);
        JSONObject json = new JSONObject();
        try {
            json.put("label", label);
        } catch (Exception e) {
            errorMessage.postValue("Add label JSON error");
            return;
        }

        RequestBody body = RequestBody.create(JSON, json.toString());

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                .patch(body)
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                errorMessage.postValue("Add label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Add label failed: " + response.code());
                }
            }
        });
    }

    public void removeLabel(String mailId, String label, String jwtToken) {
        label = normalizeLabel(label);
        JSONObject json = new JSONObject();
        try {
            json.put("label", label);
            json.put("action", "remove");
        } catch (Exception e) {
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
            @Override public void onFailure(Call call, IOException e) {
                errorMessage.postValue("Remove label failed: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorMessage.postValue("Remove label failed: " + response.code());
                }
            }
        });
    }

    public void addOrRemoveLabel(String mailId, String label, String jwtToken, boolean shouldAdd) {
        if (shouldAdd) {
            addLabel(mailId, label, jwtToken);
        } else {
            removeLabel(mailId, label, jwtToken);
        }
    }

    public void removeLabelWithCallback(String mailId, String label, String jwtToken, Runnable onSuccess) {
        label = normalizeLabel(label);
        JSONObject json = new JSONObject();
        try {
            json.put("label", label);
            json.put("action", "remove");
        } catch (Exception e) {
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
                errorMessage.postValue("Remove label failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    onSuccess.run();
                } else {
                    errorMessage.postValue("Remove label failed: " + response.code());
                }
            }
        });
    }
}
