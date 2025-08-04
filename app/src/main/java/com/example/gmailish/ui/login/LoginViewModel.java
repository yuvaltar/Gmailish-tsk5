package com.example.gmailish.ui.login;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class LoginViewModel extends AndroidViewModel {

    public MutableLiveData<Boolean> loginResult = new MutableLiveData<>();
    public MutableLiveData<String> error = new MutableLiveData<>();
    private final OkHttpClient client = new OkHttpClient();
    private String token;

    public LoginViewModel(@NonNull Application application) {
        super(application);
    }

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
                    String setCookie = response.header("Set-Cookie");

                    if (setCookie != null && setCookie.contains("token=")) {
                        token = extractTokenFromCookie(setCookie);

                        SharedPreferences prefs = getApplication()
                                .getSharedPreferences("prefs", Context.MODE_PRIVATE);
                        prefs.edit().putString("jwt", token).apply();
                    }

                    loginResult.postValue(true);
                } else {
                    error.postValue("Login failed: " + response.code());
                }
            }
        });
    }

    private String extractTokenFromCookie(String cookieHeader) {
        for (String cookie : cookieHeader.split(";")) {
            if (cookie.trim().startsWith("token=")) {
                return cookie.trim().substring("token=".length());
            }
        }
        return null;
    }

    public String getToken() {
        return token;
    }
}
