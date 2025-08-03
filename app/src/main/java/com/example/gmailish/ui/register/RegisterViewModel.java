//registerViewModel
package com.example.gmailish.ui.register;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.model.RegisterRequest;

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
    private final RegisterRequest request = new RegisterRequest();

    public boolean setName(String first, String last) {
        boolean valid = true;

        if (first == null || first.trim().isEmpty()) {
            firstNameError.setValue("First name is required");
            valid = false;
        } else {
            firstNameError.setValue(null);
            request.setFirstName(first);
        }

        if (last == null || last.trim().isEmpty()) {
            lastNameError.setValue("Last name is required");
            valid = false;
        } else {
            lastNameError.setValue(null);
            request.setLastName(last);
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
            request.setDob(dob);
        }
        if (g == null || g.trim().isEmpty()) {
            message.setValue("Please select a gender");
            valid = false;
        } else {
            request.setGender(g.trim());
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
            request.setUsername(user.trim());
        }

        if (pwd == null || pwd.length() < 8) {
            passwordError.setValue("Password must be at least 8 characters");
            valid = false;
        } else {
            passwordError.setValue(null);
            request.setPassword(pwd);
        }

        if (!pwd.equals(confirm)) {
            passwordError.setValue("Passwords do not match");
            valid = false;
        }

        return valid;
    }

    public void register(File imageFile) {
        if (imageFile == null) {
            message.postValue("Image file is required");
            return;
        }

        MultipartBody.Builder formBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("firstName", request.getFirstName())
                .addFormDataPart("lastName", request.getLastName())
                .addFormDataPart("username", request.getUsername())
                .addFormDataPart("gender", request.getGender())
                .addFormDataPart("password", request.getPassword())
                .addFormDataPart("birthdate", formatDate(request.getDob()))  // Make sure it's in YYYY-MM-DD format
                .addFormDataPart("picture", imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse("image/*")));

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
                if (response.isSuccessful()) {
                    message.postValue("Registered successfully!");
                    registrationSuccess.postValue(true);
                } else {
                    message.postValue("Server error: " + response.code() + " - " + response.body().string());
                }
            }
        });
    }

}
