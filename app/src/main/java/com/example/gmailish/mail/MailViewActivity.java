package com.example.gmailish.mail;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;

public class MailViewActivity extends AppCompatActivity {
    private TextView senderText, recipientText, subjectText, contentText, timestampText;
    private MailViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_view);

        senderText    = findViewById(R.id.senderText);
        recipientText = findViewById(R.id.recipientText);
        subjectText   = findViewById(R.id.subjectText);
        contentText   = findViewById(R.id.contentText);
        timestampText = findViewById(R.id.timestampText);

        viewModel = new ViewModelProvider(this).get(MailViewModel.class);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String jwtToken = prefs.getString("jwt", null);
        String mailId = getIntent().getStringExtra("mailId");

        // Observe mail data
        viewModel.mailData.observe(this, mail -> {
            senderText.setText("From: " + mail.optString("senderName") + " <" + mail.optString("senderId") + ">");
            recipientText.setText("To: " + mail.optString("recipientName") + " <" + mail.optString("recipientEmail") + ">");
            subjectText.setText(mail.optString("subject"));
            contentText.setText(mail.optString("content"));
            timestampText.setText(mail.optString("timestamp"));
        });

        viewModel.errorMessage.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());

        viewModel.fetchMailById(mailId, jwtToken);
    }
}
