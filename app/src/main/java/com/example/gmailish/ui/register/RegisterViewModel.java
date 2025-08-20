package com.example.gmailish.ui.register;

import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.gmailish.data.entity.UserEntity;
import com.example.gmailish.data.repository.UserRepository;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@HiltViewModel
public class RegisterViewModel extends ViewModel {

    private static final String TAG = "RegisterVM";

    public final MutableLiveData<String> firstNameError = new MutableLiveData<>();
    public final MutableLiveData<String> lastNameError = new MutableLiveData<>();
    public final MutableLiveData<String> dobError = new MutableLiveData<>();
    public final MutableLiveData<String> passwordError = new MutableLiveData<>();
    public final MutableLiveData<String> message = new MutableLiveData<>();
    public final MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();

    private String firstName;
    private String lastName;
    private Date birthdate;
    private String gender;
    private String username;
    private String password;

    private final OkHttpClient http = new OkHttpClient();
    // Fixed: Changed to the correct endpoint that matches your backend
    private static final String REGISTER_URL = "http://10.0.2.2:3000/api/users";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final UserRepository userRepository;

    @Inject
    public RegisterViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
        Log.d(TAG, "RegisterViewModel created with repository: " + (userRepository != null));
    }

    public boolean setName(String first, String last) {
        firstNameError.setValue(null);
        lastNameError.setValue(null);
        boolean ok = true;
        if (TextUtils.isEmpty(first)) { firstNameError.setValue("First name is required"); ok = false; }
        if (TextUtils.isEmpty(last))  { lastNameError.setValue("Last name is required"); ok = false; }
        if (!ok) return false;
        this.firstName = first.trim();
        this.lastName = last.trim();
        Log.d(TAG, "Name set: " + firstName + " " + lastName);
        return true;
    }

    public boolean setDobAndGender(Date dob, String gender) {
        dobError.setValue(null);
        if (dob == null) {
            dobError.setValue("Date of birth is required");
            return false;
        }
        this.birthdate = dob;
        this.gender = gender != null ? gender.trim() : "";
        Log.d(TAG, "DOB and gender set: " + birthdate + ", " + this.gender);
        return true;
    }

    public boolean setUsernameAndPassword(String username, String pass, String confirm) {
        passwordError.setValue(null);
        if (TextUtils.isEmpty(username) || username.length() < 3) {
            message.setValue("Username must be at least 3 characters");
            return false;
        }
        if (TextUtils.isEmpty(pass) || pass.length() < 8) {
            passwordError.setValue("Password must be at least 8 characters");
            return false;
        }
        if (!TextUtils.equals(pass, confirm)) {
            passwordError.setValue("Passwords do not match");
            return false;
        }
        this.username = username.trim();
        this.password = pass;
        Log.d(TAG, "Username and password set: " + this.username);
        return true;
    }

    public void register(File imageFile) {
        Log.d(TAG, "Starting registration process...");
        registrationSuccess.setValue(false);

        // Convert birthdate to YYYY-MM-DD format as expected by backend
        String birthdateStr = "";
        if (birthdate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            birthdateStr = sdf.format(birthdate);
        }

        Log.d(TAG, "Registration data - firstName: " + firstName + ", lastName: " + lastName +
                ", username: " + username + ", gender: " + gender +
                ", birthdate: " + birthdateStr + ", hasImage: " + (imageFile != null && imageFile.exists()));

        try {
            final Request request;

            // Always use multipart form data to match backend expectations
            MultipartBody.Builder mb = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("firstName", safe(firstName))
                    .addFormDataPart("lastName", safe(lastName))
                    .addFormDataPart("username", safe(username))
                    .addFormDataPart("password", safe(password))
                    .addFormDataPart("gender", safe(gender))
                    .addFormDataPart("birthdate", birthdateStr);

            // Add image file if present
            if (imageFile != null && imageFile.exists()) {
                Log.d(TAG, "Adding image to multipart request");
                mb.addFormDataPart(
                        "picture",
                        imageFile.getName(),
                        RequestBody.create(MediaType.parse("image/*"), imageFile)
                );
            }

            request = new Request.Builder()
                    .url(REGISTER_URL)
                    .post(mb.build())
                    .build();

            Log.d(TAG, "Making request to: " + REGISTER_URL);
            http.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Register network error: " + e.getMessage(), e);
                    message.postValue("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        String body = r.body() != null ? r.body().string() : "{}";
                        Log.d(TAG, "Register response code=" + r.code() + " body=" + body);

                        if (!r.isSuccessful()) {
                            // Parse error message from backend
                            String errorMsg = "Registration failed: " + r.code();
                            try {
                                JSONObject errorObj = new JSONObject(body);
                                String backendError = errorObj.optString("error", "Unknown error");
                                errorMsg = backendError;
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse error response", e);
                            }
                            message.postValue(errorMsg);
                            return;
                        }

                        final JSONObject obj;
                        try {
                            obj = new JSONObject(body);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse server response: " + body, e);
                            message.postValue("Bad server response");
                            return;
                        }

                        // Extract data from response - backend returns: id, username, email
                        String id = obj.optString("id", null);
                        if (id == null || id.isEmpty()) {
                            id = UUID.randomUUID().toString();
                            Log.d(TAG, "No ID from server, generated: " + id);
                        }

                        String uname = obj.optString("username", username);
                        String email = obj.optString("email", null);
                        String picture = null; // Backend doesn't return picture URL in register response

                        final UserEntity entity = new UserEntity(id, uname, email, picture);
                        Log.d(TAG, "Creating UserEntity -> id=" + id + ", username=" + uname + ", email=" + email);

                        ioExecutor.execute(() -> {
                            try {
                                Log.d(TAG, "Attempting to save user to Room database...");
                                userRepository.saveUser(entity);
                                Log.d(TAG, "UserEntity successfully saved to Room");

                                message.postValue("Registration successful");
                                registrationSuccess.postValue(true);
                            } catch (Exception e) {
                                Log.e(TAG, "Room save error", e);
                                message.postValue("Registration succeeded but failed to save locally: " + e.getMessage());
                            }
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "register() response processing error", e);
                        message.postValue("Unexpected error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "register() outer error", e);
            message.setValue("Unexpected error: " + e.getMessage());
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}
