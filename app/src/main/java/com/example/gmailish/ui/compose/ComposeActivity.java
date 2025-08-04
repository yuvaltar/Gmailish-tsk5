package com.example.gmailish.ui.compose;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;

public class ComposeActivity extends AppCompatActivity {

    private EditText editRecipient, editSubject, editContent;
    private Button btnSend;
    private ComposeViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        // Initialize UI elements
        editRecipient = findViewById(R.id.editRecipient);
        editSubject = findViewById(R.id.editSubject);
        editContent = findViewById(R.id.editContent);
        btnSend = findViewById(R.id.btnSend);

        // Init ViewModel
        viewModel = new ViewModelProvider(this).get(ComposeViewModel.class);

        // Observe messages and result
        viewModel.message.observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.sendSuccess.observe(this, success -> {
            if (success != null && success) {
                Toast.makeText(this, "Mail sent successfully", Toast.LENGTH_SHORT).show();
                finish(); // Return to inbox or previous screen
            }
        });

        // Send button logic
        btnSend.setOnClickListener(v -> {
            String to = editRecipient.getText().toString().trim();
            String subject = editSubject.getText().toString().trim();
            String content = editContent.getText().toString().trim();

            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String token = prefs.getString("jwt", null);

            viewModel.sendEmail(to, subject, content, token);
        });
    }
}
