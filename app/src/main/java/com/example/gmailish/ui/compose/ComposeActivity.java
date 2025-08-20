package com.example.gmailish.ui.compose;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ComposeActivity extends AppCompatActivity {


    private static final String TAG = "ComposeSave";

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

        backButton.setOnClickListener(v -> finish());
        attachButton.setOnClickListener(v ->
                Toast.makeText(this, "Attach button clicked", Toast.LENGTH_SHORT).show()
        );

        viewModel = new ViewModelProvider(this).get(ComposeViewModel.class);

        viewModel.message.observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Log.d(TAG, "VM message: " + msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // IMPORTANT: Do not write to Room here. ViewModel handles all persistence.
        viewModel.sendSuccess.observe(this, payload -> {
            Log.d(TAG, "sendSuccess observed. payload=" + payload);
            Toast.makeText(this, "Mail sent successfully", Toast.LENGTH_SHORT).show();
        });

        sendButton.setOnClickListener(v -> {
            String to = toField.getText().toString().trim();
            String subject = subjectField.getText().toString().trim();
            String content = bodyField.getText().toString().trim();
            Log.d(TAG, "Send tapped. to=" + to + ", subject=" + subject
                    + ", len(content)=" + (content != null ? content.length() : 0));
            viewModel.sendEmail(this, to, subject, content);
        });
    }
}