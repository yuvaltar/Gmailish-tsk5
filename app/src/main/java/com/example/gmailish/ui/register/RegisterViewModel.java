package com.example.gmailish.ui.register;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.content.Context;

import androidx.room.Room;

import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.dao.UserDao;
import com.example.gmailish.data.entity.UserEntity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;



public class RegisterViewModel extends ViewModel {
    public MutableLiveData<String> firstNameError = new MutableLiveData<>();
    public MutableLiveData<String> lastNameError = new MutableLiveData<>();
    public MutableLiveData<String> dobError = new MutableLiveData<>();
    public MutableLiveData<String> passwordError = new MutableLiveData<>();
    public MutableLiveData<String> message = new MutableLiveData<>();
    public MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();


    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // Minimal in-file request holder
    private static class RegisterRequest {
        String firstName;
        String lastName;
        Date dob;
        String gender;
        String username;
        String password;
    }

    private final RegisterRequest request = new RegisterRequest();

    // Room references (built lazily without Hilt)
    private AppDatabase db;
    private UserDao userDao;

    // Call this once from Activity with applicationContext before using register()
    public void setDatabaseContext(Context appContext) {
        if (db == null) {
            db = Room.databaseBuilder(appContext, AppDatabase.class, "gmailish.db")
                    .fallbackToDestructiveMigration() // dev only
                    .build();
            userDao = db.userDao();
        }
    }

    public boolean setName(String first, String last) {
        boolean valid = true;
        if (first == null || first.trim().isEmpty()) {
            firstNameError.setValue("First name is required");
            valid = false;
        } else {
            firstNameError.setValue(null);
            request.firstName = first;
        }

        if (last == null || last.trim().isEmpty()) {
            lastNameError.setValue("Last name is required");
            valid = false;
        } else {
            lastNameError.setValue(null);
            request.lastName = last;
        }
        return valid;
    }

    public boolean setDobAndGender(Date dob, String g) {
        boolean valid = true;
        if (dob == null){
            dobError.setValue("Please enter your DOB");
            valid = false;
        } else {
            dobError.setValue(null);
            request.dob = dob;
        }
        if (g == null || g.trim().isEmpty()) {
            message.setValue("Please select a gender");
            valid = false;
        } else {
            request.gender = g.trim();
        }
        return valid;
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(date);
    }

    public boolean setUsernameAndPassword(String user, String pwd, String confirm) {
        boolean valid = true;
        if (user == null || user.trim().isEmpty()) {
            message.setValue("Username is required");
            valid = false;
        } else {
            request.username = user.trim();
        }

        if (pwd == null || pwd.length() < 8) {
            passwordError.setValue("Password must be at least 8 characters");
            valid = false;
        } else {
            passwordError.setValue(null);
            request.password = pwd;
        }

        if (pwd == null || !pwd.equals(confirm)) {
            passwordError.setValue("Passwords do not match");
            valid = false;
        }
        return valid;
    }

    public void register(File imageFile) {
        if (db == null || userDao == null) {
            message.postValue("Internal error: database not initialized");
            return;
        }
        if (request.firstName == null || request.lastName == null || request.username == null
                || request.gender == null || request.password == null || request.dob == null) {
            message.postValue("Please complete all fields");
            return;
        }

        MultipartBody.Builder formBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("firstName", request.firstName)
                .addFormDataPart("lastName", request.lastName)
                .addFormDataPart("username", request.username)
                .addFormDataPart("gender", request.gender)
                .addFormDataPart("password", request.password)
                .addFormDataPart("birthdate", formatDate(request.dob));

        if (imageFile != null && imageFile.exists()) {
            formBuilder.addFormDataPart("picture", imageFile.getName(),
                    RequestBody.create(imageFile, MediaType.parse("image/*")));
        }

        MultipartBody requestBody = formBuilder.build();

        Request httpRequest = new Request.Builder()
                .url("http://10.0.2.2:3000/api/users")
                .post(requestBody)
                .build();

        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                message.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        message.postValue("Server error: " + r.code() + " - " + (r.body() != null ? r.body().string() : ""));
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "{}";
                    try {
                        JSONObject json = new JSONObject(body);

                        // Map JSON -> UserEntity (manual to avoid adding a mapper call from Java)
                        // Expected fields: id, firstName, lastName, username, email, gender, birthdate, picture
                        // Adjust keys if your backend differs.
                        String id = json.optString("id");
                        String firstName = json.optString("firstName", request.firstName);
                        String lastName = json.optString("lastName", request.lastName);
                        String username = json.optString("username", request.username);
                        String email = json.optString("email", "");
                        String gender = json.optString("gender", request.gender);
                        String picture = json.optString("picture", "");

                        // birthdate: server may send ISO date or millis; we’ll store original DOB from request
                        Date birthdate = request.dob;

                        UserEntity entity = new UserEntity(
                                id,
                                firstName,
                                lastName,
                                username,
                                email,
                                gender,
                                birthdate,
                                picture
                        );

                        // Save to Room off the main thread
                        new Thread(() -> {
                            try {
                                userDao.upsert(entity);


                                // Immediate readback to verify write
                                UserEntity check = userDao.getByUsername(entity.getUsername());
                                if (check != null) {
                                    registrationSuccess.postValue(true);
                                    message.postValue("Saved to Room: " + check.getUsername());
                                } else {
                                    message.postValue("Insert done but readback returned null");
                                }
                            } catch (Exception ex) {
                                message.postValue("DB error: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }).start();

                    } catch (JSONException ex) {
                        message.postValue("Parse error: " + ex.getMessage());
                    }
                }
            }
        });
    }
}