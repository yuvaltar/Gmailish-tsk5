package com.example.gmailish.ui.login;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class LoginViewModel extends ViewModel {
    public MutableLiveData<Boolean> loginResult = new MutableLiveData<>();
    public MutableLiveData<String> error = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();

    public void login(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            error.setValue("Please enter both email and password");
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
        } catch (Exception e) {
            error.setValue("JSON error");
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/tokens")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                error.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    loginResult.postValue(true);
                } else {
                    error.postValue("Login failed: " + response.code());
                }
            }
        });
    }
}
