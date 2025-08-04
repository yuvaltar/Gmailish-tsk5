package com.example.gmailish.ui.compose;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class ComposeViewModel extends ViewModel {

    public MutableLiveData<String> message = new MutableLiveData<>();
    public MutableLiveData<Boolean> sendSuccess = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json");

    public void sendEmail(String to, String subject, String content, String token) {
        if (to.isEmpty() || subject.isEmpty() || content.isEmpty()) {
            message.setValue("Please fill all fields");
            return;
        }

        if (token == null || token.isEmpty()) {
            message.setValue("Missing authentication token");
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("to", to);
            json.put("subject", subject);
            json.put("content", content);
        } catch (Exception e) {
            message.setValue("Error building request");
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails")
                .post(body)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                message.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String errorMsg = "Unknown error";
                if (!response.isSuccessful()) {
                    if (response.body() != null) {
                        errorMsg = response.body().string();
                    }
                    message.postValue("Failed to send: " + response.code() + "\n" + errorMsg);
                } else {
                    sendSuccess.postValue(true);
                }
            }

        });
    }
}
