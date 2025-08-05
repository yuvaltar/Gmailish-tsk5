package com.example.gmailish.ui.compose;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;

public class ComposeActivity extends AppCompatActivity {

    private EditText toField, subjectField, bodyField;
    private ImageView sendButton, backButton, attachButton;
    private ComposeViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        toField = findViewById(R.id.toField);
        subjectField = findViewById(R.id.subjectField);
        bodyField = findViewById(R.id.bodyField);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        attachButton = findViewById(R.id.attachButton);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        Log.d("ComposeActivity", "Token: " + token);

        backButton.setOnClickListener(v -> finish());

        attachButton.setOnClickListener(v ->
                Toast.makeText(this, "Attach button clicked", Toast.LENGTH_SHORT).show());

        viewModel = new ViewModelProvider(this).get(ComposeViewModel.class);

        viewModel.message.observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.sendSuccess.observe(this, success -> {
            if (success != null && success) {
                Toast.makeText(this, "Mail sent successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        sendButton.setOnClickListener(v -> {
            String to = toField.getText().toString().trim();
            String subject = subjectField.getText().toString().trim();
            String content = bodyField.getText().toString().trim();
            viewModel.sendEmail(to, subject, content, token);
        });
    }
}
