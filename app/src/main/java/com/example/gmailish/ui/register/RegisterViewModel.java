package com.example.gmailish.ui.register;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.model.RegisterRequest;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;


public class RegisterViewModel extends ViewModel {
    private MutableLiveData<String> firstName = new MutableLiveData<>();
    public MutableLiveData<String> firstNameError = new MutableLiveData<>();
    public MutableLiveData<String> lastNameError = new MutableLiveData<>();
    public MutableLiveData<String> dobError = new MutableLiveData<>();
    private MutableLiveData<String> lastName = new MutableLiveData<>();
    private MutableLiveData<String> dob = new MutableLiveData<>();
    private MutableLiveData<String> gender = new MutableLiveData<>();
    private MutableLiveData<String> username = new MutableLiveData<>();
    private MutableLiveData<String> password = new MutableLiveData<>();
    public MutableLiveData<String> message = new MutableLiveData<>();
    public MutableLiveData<String> passwordError = new MutableLiveData<>();
    public MutableLiveData<String> confirmError = new MutableLiveData<>();
    public MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();



    private final OkHttpClient client = new OkHttpClient();

    private RegisterRequest request = new RegisterRequest();


    public void setName(String first, String last) {
        boolean valid = true;

        if (first == null || first.trim().isEmpty()) {
            firstNameError.setValue("First name is required");
            valid = false;
        } else {
            firstNameError.setValue(null);
        }

        if (last == null || last.trim().isEmpty()) {
            lastNameError.setValue("Last name is required");
            valid = false;
        } else {
            lastNameError.setValue(null);
        }

        if (valid) {
            request.setFirstName(first);
            request.setLastName(last);
        }
    }

    public void setDobAndGender(String d, String g) {
        String dobValue = dob.getValue();
        if (dobValue == null || !dobValue.matches("\\d{2}/\\d{2}/\\d{4}")) {
            dobError.setValue("Invalid date format (dd/mm/yyyy)");
        } else {
            dobError.setValue(null);
            request.setDob(String.valueOf(dob));
            request.setGender(String.valueOf(gender));
        }
    }

    public void setUsername(String user) {
        username.setValue(user);
    }

    public void setPassword(String pwd) {
        password.setValue(pwd);
    }

    public void register(String confirmPassword) {
        if (!confirmPassword.equals(password.getValue())) {
            message.setValue("Passwords do not match.");
            return;
        }

        if (password.getValue().length() < 8) {
            message.setValue("Password must be at least 8 characters.");
            return;
        }

        RegisterRequest user = new RegisterRequest(
                firstName.getValue(),
                lastName.getValue(),
                dob.getValue(),
                gender.getValue(),
                username.getValue(),
                password.getValue()
        );
        Gson gson = new Gson();
        String json = gson.toJson(user);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder().url("http://10.0.2.2:3000/api/users/register").post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                message.postValue("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    message.postValue("Registered successfully!");
                    registrationSuccess.postValue(true);
                    // Optional: trigger navigation event via LiveData
                } else {
                    message.postValue("Error: " + response.code() + " - " + response.body().string());
                }
            }
        });
    }
}
