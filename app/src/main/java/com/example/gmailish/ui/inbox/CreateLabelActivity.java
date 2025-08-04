package com.example.gmailish.ui.inbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gmailish.R;

import org.json.JSONObject;

import java.io.IOException;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_label);

        labelInput = findViewById(R.id.labelInput);
        saveButton = findViewById(R.id.saveLabelButton);
        cancelButton = findViewById(R.id.cancelLabelButton);

        cancelButton.setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> {
            String name = labelInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

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
                    .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(CreateLabelActivity.this, "Network error", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(CreateLabelActivity.this, "Label created", Toast.LENGTH_SHORT).show();
                            finish();  // go back to Inbox
                        } else {
                            Toast.makeText(CreateLabelActivity.this, "Failed to create label", Toast.LENGTH_SHORT).show();
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
}
