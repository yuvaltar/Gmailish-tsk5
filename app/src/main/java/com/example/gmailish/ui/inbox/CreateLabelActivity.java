package com.example.gmailish.ui.inbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gmailish.R;
import com.example.gmailish.data.db.AppDatabase; // Room DB singleton
import com.example.gmailish.data.entity.LabelEntity; // Room entity
import com.example.gmailish.data.repository.LabelRepository; // Repository wrapper

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID; // fallback id if server doesn't return one
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CreateLabelActivity extends AppCompatActivity {

    private EditText labelInput;
    private Button saveButton, cancelButton;

    private final OkHttpClient client = new OkHttpClient();
    private static final String LABELS_URL = "http://10.0.2.2:3000/api/labels";
    private static final MediaType JSON = MediaType.parse("application/json");

    // Keep references needed to save to Room
    private LabelRepository labelRepository;
    private ExecutorService ioExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_label);

        labelInput = findViewById(R.id.labelInput);
        saveButton = findViewById(R.id.saveLabelButton);
        cancelButton = findViewById(R.id.cancelLabelButton);

        // Initialize repository and IO executor
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        labelRepository = new LabelRepository(db.labelDao(), db.mailLabelDao());
        ioExecutor = Executors.newSingleThreadExecutor();

        cancelButton.setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> {
            String name = labelInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Build request body for the server
            JSONObject json = new JSONObject();
            try {
                json.put("name", name);
            } catch (Exception e) {
                Toast.makeText(this, "Error building request", Toast.LENGTH_SHORT).show();
                return;
            }

            Request request = new Request.Builder()
                    .url(LABELS_URL)
                    .header("Authorization", "Bearer " + getJwtToken())
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() ->
                            Toast.makeText(CreateLabelActivity.this, "Network error", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() ->
                                Toast.makeText(CreateLabelActivity.this, "Failed to create label", Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "{}";
                    String createdId = null;
                    String createdName = null;
                    String ownerId = null;
                    try {
                        JSONObject obj = new JSONObject(body);
                        // Adjust keys if your backend uses different fields (e.g. "_id")
                        createdId = obj.optString("id", null);
                        createdName = obj.optString("name", null);
                        ownerId = obj.optString("ownerId", null);
                    } catch (Exception ignore) {
                        // Fall back to local values below
                    }

                    if (createdName == null || createdName.isEmpty()) {
                        createdName = name; // from the input field
                    }
                    if (ownerId == null || ownerId.isEmpty()) {
                        ownerId = getCurrentUserIdFromPrefs(); // saved by InboxViewModel.loadCurrentUser
                    }
                    if (createdId == null || createdId.isEmpty()) {
                        // If server doesn't return id, generate a local one
                        createdId = UUID.randomUUID().toString();
                    }

                    LabelEntity labelEntity = new LabelEntity(
                            createdId,
                            ownerId != null ? ownerId : "",
                            createdName
                    );

                    // Persist to Room on background thread via Java-friendly wrapper
                    ioExecutor.execute(() -> {
                        try {
                            labelRepository.saveLabelBlocking(labelEntity);
                            runOnUiThread(() -> {
                                Toast.makeText(CreateLabelActivity.this, "Label created", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                    Toast.makeText(CreateLabelActivity.this, "Local save failed", Toast.LENGTH_SHORT).show()
                            );
                        }
                    });
                }
            });
        });
    }

    private String getJwtToken() {
        SharedPreferences prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return prefs.getString("jwt", null);
    }

    private String getCurrentUserIdFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_id", null);
    }
}
