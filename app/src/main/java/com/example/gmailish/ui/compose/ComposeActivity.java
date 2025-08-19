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
import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.repository.MailRepository;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;

public class ComposeActivity extends AppCompatActivity {

    private static final String TAG = "ComposeSave";

    private EditText toField, subjectField, bodyField;
    private ImageView sendButton, backButton;
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

        // Prefill from MailViewActivity (Reply/Forward)
        applyPrefillFromExtras();

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        Log.d(TAG, "JWT token loaded: " + token);

        backButton.setOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(ComposeViewModel.class);

        viewModel.message.observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Log.d(TAG, "VM message: " + msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // IMPORTANT: sendSuccess must emit a JSON payload String from ComposeViewModel
        viewModel.sendSuccess.observe(this, payload -> {
            Log.d(TAG, "sendSuccess observed. payload=" + payload);
            if (payload == null || payload.isEmpty()) return;

            try {
                JSONObject json = new JSONObject(payload);
                String to = json.optString("to");
                String subject = json.optString("subject");
                String content = json.optString("content");
                String serverMailId = json.optString("id", null);

                Log.d(TAG, "Parsed payload -> to=" + to + ", subject=" + subject +
                        ", len(content)=" + (content != null ? content.length() : 0) +
                        ", serverId=" + serverMailId);

                // Current user (saved when loading /users/me)
                SharedPreferences sp = getSharedPreferences("prefs", MODE_PRIVATE);
                String senderId = sp.getString("user_id", null);
                String senderName = sp.getString("username", "Me");
                Log.d(TAG, "Sender from prefs -> id=" + senderId + ", name=" + senderName);

                if (senderId != null) {
                    String baseId = (serverMailId != null && !serverMailId.isEmpty())
                            ? serverMailId
                            : UUID.randomUUID().toString();
                    Date now = new Date();

                    // Fallback recipient identity: use email
                    String recipientId = to;
                    String recipientName = to;

                    MailEntity senderMail = new MailEntity(
                            baseId + "_s",
                            senderId,
                            senderName != null ? senderName : "Me",
                            recipientId,
                            recipientName,
                            to,
                            subject,
                            content,
                            now,
                            senderId,   // owner is the sender
                            true,       // read for sender
                            false
                    );

                    MailEntity recipientMail = new MailEntity(
                            baseId + "_r",
                            senderId,
                            senderName != null ? senderName : "Me",
                            recipientId,
                            recipientName,
                            to,
                            subject,
                            content,
                            now,
                            recipientId, // owner is the recipient
                            false,       // unread for recipient
                            false
                    );

                    Log.d(TAG, "Prepared entities. senderId=" + senderMail.getId() +
                            ", recipientId=" + recipientMail.getId());

                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            Log.d(TAG, "Opening DB singleton...");
                            AppDatabase db = AppDatabase.Companion.getInstance(getApplicationContext());
                            Log.d(TAG, "DB opened. Creating repository...");
                            MailRepository repo = new MailRepository(db.mailDao(), db.labelDao(), db.mailLabelDao());

                            Log.d(TAG, "Saving mails...");
                            repo.saveMailsBlocking(Arrays.asList(senderMail, recipientMail));
                            Log.d(TAG, "Save completed.");
                        } catch (Throwable e) {
                            Log.e(TAG, "Room save error", e);
                        }
                    });
                } else {
                    Log.w(TAG, "SenderId is null. Not saving to Room.");
                }

                Toast.makeText(this, "Mail sent successfully", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Save to Room parse error", e);
            }

            finish();
        });

        sendButton.setOnClickListener(v -> {
            String to = toField.getText().toString().trim();
            String subject = subjectField.getText().toString().trim();
            String content = bodyField.getText().toString().trim();
            Log.d(TAG, "Send tapped. to=" + to + ", subject=" + subject +
                    ", len(content)=" + (content != null ? content.length() : 0));
            viewModel.sendEmail(to, subject, content, token);
        });
    }

    private void applyPrefillFromExtras() {
        if (getIntent() == null) return;

        String mode    = getIntent().getStringExtra("EXTRA_MODE");   // "reply" or "forward"
        String to      = getIntent().getStringExtra("EXTRA_TO");
        String subject = getIntent().getStringExtra("EXTRA_SUBJECT");
        String body    = getIntent().getStringExtra("EXTRA_BODY");

        // >>> FIX: ensure "to" is an email. If it's a plain name, append "@gmailish.com".
        if (to != null && !to.isEmpty()) {
            toField.setText(normalizeToEmail(to));
        }
        if (subject != null) subjectField.setText(subject);
        if (body != null) bodyField.setText(body);

        // Optional UX: focus the right field
        if ("forward".equals(mode)) {
            toField.requestFocus();          // user must choose a recipient
        } else if ("reply".equals(mode)) {
            bodyField.requestFocus();
            bodyField.setSelection(bodyField.getText().length());
        }
    }

    /**
     * Convert various "to" values into an email form.
     * - "Name <user@host>" -> "user@host"
     * - already an email -> unchanged
     * - plain name -> "name@gmailish.com"
     */
    private String normalizeToEmail(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // Case: "Name <email@host>"
        int lt = s.indexOf('<');
        int gt = s.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String inside = s.substring(lt + 1, gt).trim();
            if (inside.contains("@")) return inside;
        }

        // Already looks like an email
        if (s.contains("@")) return s;

        // Plain name -> sanitize lightly and append domain
        String local = s.toLowerCase()
                .replaceAll("\\s+", ".");        // spaces -> dots
        return local + "@gmailish.com";
    }
}
