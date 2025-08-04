package com.example.gmailish.ui.inbox;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
import android.util.Log;

public class InboxViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Email>> emailsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final OkHttpClient client = new OkHttpClient();

    public InboxViewModel(Application application) {
        super(application);
    }

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
                .url("http://10.0.2.2:3000/api/mails/search/" + query)
                .header("Authorization", "Bearer " + getJwtToken())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SearchError", "Network error: " + e.getMessage());
                errorLiveData.postValue("Search error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("SearchError", "Search failed: " + response.code());
                    errorLiveData.postValue("Search failed: " + response.code());
                    return;
                }

                String jsonStr = response.body().string();
                Log.d("SearchRaw", jsonStr);

                try {
                    JSONArray jsonArray = new JSONArray(jsonStr);
                    List<Email> parsedEmails = new ArrayList<>();

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

                    Log.d("SearchResult", "Parsed emails: " + parsedEmails.size());
                    emailsLiveData.postValue(parsedEmails);

                } catch (Exception e) {
                    Log.e("SearchError", "JSON parse error: " + e.getMessage());
                    errorLiveData.postValue("JSON parse error: " + e.getMessage());
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

    private String getJwtToken() {
        SharedPreferences prefs = getApplication()
                .getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return prefs.getString("jwt", null);
    }
    public void loadEmailsByLabel(String label) {
        errorLiveData.setValue(null);

        String token = getJwtToken();
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
                errorLiveData.postValue("Failed to load '" + label + "': " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorLiveData.postValue("Server error " + response.code() + " loading '" + label + "'");
                    return;
                }

                List<Email> parsed = parseEmailList(response);
                if (parsed != null) {
                    emailsLiveData.postValue(parsed);
                }
            }
        });
    }

}
