package com.example.gmailish.mail;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.utils.TokenManager;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class MailViewModel extends ViewModel {

    public MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public MutableLiveData<JSONObject> mailData = new MutableLiveData<>();

    private final OkHttpClient client = new OkHttpClient();

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
}
