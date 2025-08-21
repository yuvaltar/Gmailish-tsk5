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

import com.example.gmailish.data.db.AppDbProvider;

import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.repository.MailRepository;

import org.json.JSONObject;


import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ComposeActivity extends AppCompatActivity {

    private static final String TAG = "ComposeSave";

    // If you click a draft in Inbox, EmailAdapter should pass this extra
    public static final String EXTRA_DRAFT_ID   = "EXTRA_DRAFT_ID";
    public static final String EXTRA_MODE       = "EXTRA_MODE";     // "reply" | "forward"
    public static final String EXTRA_TO         = "EXTRA_TO";
    public static final String EXTRA_SUBJECT    = "EXTRA_SUBJECT";
    public static final String EXTRA_BODY       = "EXTRA_BODY";

    private EditText toField, subjectField, bodyField;
    private ImageView sendButton, backButton;
    private ComposeViewModel viewModel;

    // Draft state
    private String draftId = null;           // null = new draft; otherwise edit mode
    private boolean loadedExistingDraft = false;

    // Repos / DB
    private MailRepository mailRepo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Cached user
    private String ownerId;
    private String ownerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        toField = findViewById(R.id.toField);
        subjectField = findViewById(R.id.subjectField);
        bodyField = findViewById(R.id.bodyField);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);

        // DB + repo (local only for drafts)
        AppDatabase db = AppDbProvider.get(getApplicationContext());
        mailRepo = new MailRepository(db.mailDao(), db.labelDao(), db.mailLabelDao());

        // Current user (saved by InboxVM / HeaderManager)
        SharedPreferences sp = getSharedPreferences("prefs", MODE_PRIVATE);
        ownerId = sp.getString("user_id", null);
        ownerName = sp.getString("username", "Me");

        viewModel = new ViewModelProvider(this).get(ComposeViewModel.class);

        viewModel.message.observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Log.d(TAG, "VM message: " + msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

viewModel.sendSuccess.observe(this, payload -> {
    Log.d(TAG, "sendSuccess observed. payload=" + payload);

    // Parse payload (from main)
    if (payload != null && !payload.isEmpty()) {
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

                // If you actually want to persist locally, do it here (non-blocking):
                // io.execute(() -> mailRepo.saveSentAndInboxCopies(senderMail, recipientMail));
            }
        } catch (Exception e) {
            Log.e(TAG, "Save to Room parse error", e);
        }
    }

    // Draft cleanup (from mailDraft2)
    if (draftId != null) {
        io.execute(() -> {
            try {
                mailRepo.deleteMailLocal(draftId);
                Log.d(TAG, "Deleted local draft after send: " + draftId);
            } catch (Throwable t) {
                Log.e(TAG, "delete draft after send failed: " + t.getMessage());
            }
        });
    }

    finish();
});

// Keep the back-button draft-save behavior (from mailDraft2)
backButton.setOnClickListener(v -> {
    // Save draft (if needed) then finish
    maybeSaveDraftAndFinish();
});


        sendButton.setOnClickListener(v -> {
            String to = toField.getText().toString().trim();
            String subject = subjectField.getText().toString().trim();
            String content = bodyField.getText().toString().trim();
            Log.d(TAG, "Send tapped. to=" + to + ", subject=" + subject
                    + ", len(content)=" + (content != null ? content.length() : 0));
            viewModel.sendEmail(this, to, subject, content);
        });

        // Figure out how we opened the composer
        draftId = getIntent() != null ? getIntent().getStringExtra(EXTRA_DRAFT_ID) : null;
        if (draftId != null && !draftId.isEmpty()) {
            // Edit existing draft
            loadDraftFromDb(draftId);
        } else {
            // Reply / forward / fresh compose prefill
            applyPrefillFromExtras();
        }
    }

    // ---- Drafts: load/edit/save ---------------------------------------------

    private void loadDraftFromDb(String id) {
        io.execute(() -> {
            try {
                MailEntity m = mailRepo.getByIdSync(id);
                if (m != null) {
                    loadedExistingDraft = true;
                    String to = m.getRecipientEmail();
                    String subj = m.getSubject();
                    String body = m.getContent();

                    runOnUiThread(() -> {
                        if (to != null) toField.setText(to);
                        if (subj != null) subjectField.setText(subj);
                        if (body != null) bodyField.setText(body);
                        bodyField.requestFocus();
                        bodyField.setSelection(bodyField.getText().length());
                    });
                } else {
                    // If somehow draft id not found, just behave like new compose
                    runOnUiThread(this::applyPrefillFromExtras);
                }
            } catch (Throwable t) {
                Log.e(TAG, "loadDraftFromDb error: " + t.getMessage(), t);
                runOnUiThread(this::applyPrefillFromExtras);
            }
        });
    }

    private void maybeSaveDraftAndFinish() {
        if (fieldsAllEmpty()) {
            // If we were editing an existing draft and now everything is empty, delete it
            if (draftId != null) {
                io.execute(() -> {
                    try {
                        mailRepo.deleteMailLocal(draftId);
                        Log.d(TAG, "Draft cleared, deleted: " + draftId);
                    } catch (Throwable t) {
                        Log.e(TAG, "delete empty draft failed: " + t.getMessage());
                    }
                    runOnUiThread(this::finish);
                });
            } else {
                finish();
            }
            return;
        }

        saveDraft(() -> runOnUiThread(() -> {
            Toast.makeText(this, "Saved to Drafts", Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Lightweight auto-save when user backgrounds the app or navigates away
        if (!isFinishing() && !fieldsAllEmpty()) {
            saveDraft(null); // fire-and-forget
        }
    }

    private boolean fieldsAllEmpty() {
        return isEmpty(toField.getText().toString())
                && isEmpty(subjectField.getText().toString())
                && isEmpty(bodyField.getText().toString());
    }

    private void saveDraft(Runnable onDoneUi) {
        final String to = normalizeToEmail(toField.getText().toString().trim());
        final String subject = subjectField.getText().toString().trim();
        final String content = bodyField.getText().toString().trim();
        final Date now = new Date();

        io.execute(() -> {
            try {
                if (ownerId == null) ownerId = "me"; // very last-resort

                // <— THIS is the key line
                draftId = mailRepo.upsertDraftLocal(draftId, ownerId, to, subject, content, now);

                Log.d(TAG, "Draft saved locally with id=" + draftId);
                if (onDoneUi != null) onDoneUi.run();
            } catch (Throwable t) {
                Log.e(TAG, "saveDraft error: " + t.getMessage(), t);
                if (onDoneUi != null) onDoneUi.run();
            }
        });
    }


    // ------------------------------------------------------------------------

    private void applyPrefillFromExtras() {
        if (getIntent() == null) return;

        String mode    = getIntent().getStringExtra(EXTRA_MODE);   // "reply" or "forward"
        String to      = getIntent().getStringExtra(EXTRA_TO);
        String subject = getIntent().getStringExtra(EXTRA_SUBJECT);
        String body    = getIntent().getStringExtra(EXTRA_BODY);

        if (to != null && !to.isEmpty()) {
            toField.setText(normalizeToEmail(to));
        }
        if (subject != null) subjectField.setText(subject);
        if (body != null) bodyField.setText(body);

        if ("forward".equals(mode)) {
            toField.requestFocus();
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
                .replaceAll("\\s+", ".");
        return local + "@gmailish.com";
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
