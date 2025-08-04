package com.example.gmailish.ui.compose;

import android.content.SharedPreferences;
import android.os.Bundle;
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

public class ComposeActivity extends AppCompatActivity {

    private EditText editRecipient, editSubject, editContent;
    private Button btnSend;
    private final OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON = MediaType.parse("application/json");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        editRecipient = findViewById(R.id.editRecipient);
        editSubject = findViewById(R.id.editSubject);
        editContent = findViewById(R.id.editContent);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> sendEmail());
    }

    private void sendEmail() {
        String recipient = editRecipient.getText().toString().trim();
        String subject = editSubject.getText().toString().trim();
        String content = editContent.getText().toString().trim();

        if (recipient.isEmpty() || subject.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        if (token == null) {
            Toast.makeText(this, "Missing JWT", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("recipientEmail", recipient);
            json.put("subject", subject);
            json.put("content", content);
        } catch (Exception e) {
            Toast.makeText(this, "Error building JSON", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/mails")
                .post(body)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(ComposeActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(ComposeActivity.this, "Mail sent successfully", Toast.LENGTH_SHORT).show();
                        finish(); // go back to inbox
                    } else {
                        Toast.makeText(ComposeActivity.this, "Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
