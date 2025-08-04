package com.example.gmailish.ui.inbox;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.model.Email;

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

public class InboxViewModel extends ViewModel {

    private final MutableLiveData<List<Email>> emailsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final OkHttpClient client = new OkHttpClient();

    public LiveData<List<Email>> getEmails() {
        return emailsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public void loadEmails(String jwtToken) {
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails")
                .header("Authorization", "Bearer " + jwtToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errorLiveData.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorLiveData.postValue("Error code: " + response.code());
                    return;
                }

                List<Email> parsedEmails = parseEmailList(response);
                if (parsedEmails != null) {
                    emailsLiveData.postValue(parsedEmails);
                }
            }
        });
    }

    public void searchEmails(String query) {
        errorLiveData.setValue(null);

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails/search?query=" + query)
                .header("Authorization", "Bearer " + getJwtToken()) // Optional: handle JWT more cleanly
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                errorLiveData.postValue("Search error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorLiveData.postValue("Search failed: " + response.code());
                    return;
                }

                List<Email> parsedEmails = parseEmailList(response);
                if (parsedEmails != null) {
                    emailsLiveData.postValue(parsedEmails);
                }
            }
        });
    }

    private List<Email> parseEmailList(Response response) throws IOException {
        try {
            List<Email> parsedEmails = new ArrayList<>();
            JSONArray jsonArray = new JSONArray(response.body().string());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                parsedEmails.add(new Email(
                        obj.optString("senderName"),
                        obj.optString("subject"),
                        obj.optString("content"),
                        obj.optString("timestamp"),
                        obj.optBoolean("read"),
                        obj.optBoolean("starred")
                ));
            }

            return parsedEmails;
        } catch (Exception e) {
            errorLiveData.postValue("JSON parse error: " + e.getMessage());
            return null;
        }
    }

    // Optional helper if you want to fetch JWT internally (instead of passing it into searchEmails)
    private String getJwtToken() {
        return ""; // implement this if needed (via Application context + SharedPreferences)
    }
}
