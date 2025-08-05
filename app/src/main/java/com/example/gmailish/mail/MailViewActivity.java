package com.example.gmailish.mail;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MailViewActivity extends AppCompatActivity {

    private TextView senderText, recipientText, subjectText, contentText, timestampText, senderIcon;
    private ImageButton replyButton, forwardButton, starButton, deleteButton, archiveButton, menuButton, backButton;
    private MailViewModel viewModel;
    private String mailId;
    private boolean isStarred;
    private String jwtToken;
    private JSONArray currentLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_view);

        // Bind TextViews
        senderText = findViewById(R.id.senderText);
        recipientText = findViewById(R.id.recipientText);
        subjectText = findViewById(R.id.subjectText);
        contentText = findViewById(R.id.contentText);
        timestampText = findViewById(R.id.timestampText);
        senderIcon = findViewById(R.id.senderIcon);

        // Bind Buttons
        replyButton = findViewById(R.id.replyButton);
        forwardButton = findViewById(R.id.forwardButton);
        starButton = findViewById(R.id.starButton);
        deleteButton = findViewById(R.id.deleteButton);
        archiveButton = findViewById(R.id.archiveButton);
        menuButton = findViewById(R.id.menuButton);
        backButton = findViewById(R.id.backButton);


        viewModel = new ViewModelProvider(this).get(MailViewModel.class);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        jwtToken = prefs.getString("jwt", null);
        mailId = getIntent().getStringExtra("mailId");

        viewModel.mailData.observe(this, mail -> {
            if (mail != null) {
                senderText.setText(mail.optString("senderName"));
                recipientText.setText("To: " + mail.optString("recipientName") + " <" + mail.optString("recipientEmail") + ">");
                subjectText.setText(mail.optString("subject"));
                contentText.setText(mail.optString("content"));

                String rawTime = mail.optString("timestamp");
                try {
                    Instant instant = Instant.parse(rawTime);
                    Date date = Date.from(instant);
                    String formatted = new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
                    timestampText.setText(formatted);
                } catch (Exception e) {
                    timestampText.setText(rawTime);
                }

                String senderName = mail.optString("senderName");
                if (!senderName.isEmpty()) {
                    senderIcon.setText(senderName.substring(0, 1).toUpperCase());
                }

                currentLabels = mail.optJSONArray("labels");
                isStarred = currentLabels.toString().contains("starred");
                updateStarIcon();
            }
        });

        viewModel.errorMessage.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());

        viewModel.fetchMailById(mailId, jwtToken);

        replyButton.setOnClickListener(v ->
                Toast.makeText(this, "Reply (not implemented yet)", Toast.LENGTH_SHORT).show());

        forwardButton.setOnClickListener(v ->
                Toast.makeText(this, "Forward (not implemented yet)", Toast.LENGTH_SHORT).show());

        starButton.setOnClickListener(v -> {
            boolean willBeStarred = !isStarred;
            viewModel.addOrRemoveLabel(mailId, "starred", jwtToken, willBeStarred);
            isStarred = willBeStarred;
            updateStarIcon();
        });

        deleteButton.setOnClickListener(v -> {
            removeAllInboxLabels(() -> {
                viewModel.addLabel(mailId, "trash", jwtToken);
                Toast.makeText(this, "Moved to Trash", Toast.LENGTH_SHORT).show();
                finish();
            });
        });

        archiveButton.setOnClickListener(v -> {
            removeAllInboxLabels(() -> {
                viewModel.addLabel(mailId, "archive", jwtToken);
                Toast.makeText(this, "Archived", Toast.LENGTH_SHORT).show();
                finish();
            });
        });

        backButton.setOnClickListener(v -> onBackPressed());

        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MailViewActivity.this, v);
            popup.getMenuInflater().inflate(R.menu.mail_options_menu, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.menu_move_to) {
                    showMoveToDialog();
                    return true;
                } else if (id == R.id.menu_label_as) {
                    Toast.makeText(this, "Label as (not implemented yet)", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_report_spam) {
                    removeAllInboxLabels(() -> {
                        viewModel.addLabel(mailId, "spam", jwtToken);
                        Toast.makeText(this, "Reported as spam", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return true;
                }

                return false;
            });

            popup.show();
        });
    }

    private void updateStarIcon() {
        starButton.setImageResource(isStarred ?
                R.drawable.ic_star : R.drawable.ic_star);
    }

    private void removeLabelsSequentially(List<String> labels, int index, Runnable onComplete) {
        if (index >= labels.size()) {
            onComplete.run();
            return;
        }

        String label = labels.get(index);
        viewModel.removeLabelWithCallback(mailId, label, jwtToken, () ->
                removeLabelsSequentially(labels, index + 1, onComplete)
        );
    }

    private void removeAllInboxLabels(Runnable onComplete) {
        if (currentLabels == null) {
            onComplete.run();
            return;
        }

        List<String> labelsToRemove = new ArrayList<>();
        for (int i = 0; i < currentLabels.length(); i++) {
            try {
                String label = currentLabels.getString(i);
                if (!label.equals("starred") && isInboxLabel(label)) {
                    // Normalize inbox to primary
                    if (label.equalsIgnoreCase("inbox")) {
                        labelsToRemove.add("primary");
                    } else {
                        labelsToRemove.add(label);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        removeLabelsSequentially(labelsToRemove, 0, onComplete);
    }


    private void showMoveToDialog() {
        String[] folderNames = {"Primary", "Promotions", "Social", "Updates", "Spam", "Trash", "Drafts", "Starred", "Important"};
        String[] labelValues = {"primary", "promotions", "social", "updates", "spam", "trash", "drafts", "starred", "important"};

        new AlertDialog.Builder(MailViewActivity.this)
                .setTitle("Move to")
                .setItems(folderNames, (dialog, which) -> {
                    String targetLabel = labelValues[which];
                    removeAllInboxLabels(() -> {
                        viewModel.addLabel(mailId, targetLabel, jwtToken);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Moved to " + folderNames[which], Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isInboxLabel(String label) {
        return label.equalsIgnoreCase("primary") || label.equalsIgnoreCase("inbox") ||
                label.equalsIgnoreCase("promotions") || label.equalsIgnoreCase("social") ||
                label.equalsIgnoreCase("updates") || label.equalsIgnoreCase("trash") ||
                label.equalsIgnoreCase("drafts") || label.equalsIgnoreCase("spam") ||
                label.equalsIgnoreCase("archive") || label.equalsIgnoreCase("important");
    }
}
