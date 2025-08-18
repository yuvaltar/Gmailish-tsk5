package com.example.gmailish.mail;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
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
        // initialize ViewModel with application context so it can access Room
        viewModel.init(getApplicationContext());

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
                    String formatted = formatIso8601ToMonthDay(rawTime);
                    timestampText.setText(formatted);
                } catch (Exception e) {
                    timestampText.setText(rawTime);
                }

                String senderName = mail.optString("senderName");
                if (senderName != null && !senderName.isEmpty()) {
                    senderIcon.setText(senderName.substring(0, 1).toUpperCase());
                }

                currentLabels = mail.optJSONArray("labels");
                isStarred = currentLabels != null && currentLabels.toString().contains("starred");
                updateStarIcon();
            }
        });

        viewModel.errorMessage.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());

        viewModel.fetchMailById(mailId, jwtToken);

        viewModel.markAsRead(mailId, jwtToken);

        replyButton.setOnClickListener(v ->
                Toast.makeText(this, "Reply (not implemented yet)", Toast.LENGTH_SHORT).show());

        forwardButton.setOnClickListener(v ->
                Toast.makeText(this, "Forward (not implemented yet)", Toast.LENGTH_SHORT).show());

        starButton.setOnClickListener(v -> {
            boolean willBeStarred = !isStarred;
            // supply context so VM can update Room after server success
            viewModel.addOrRemoveLabel(mailId, "starred", jwtToken, willBeStarred, getApplicationContext());
            isStarred = willBeStarred;
            updateStarIcon();
        });

        deleteButton.setOnClickListener(v -> {
            // Hard delete
            viewModel.deleteMail(mailId, jwtToken, getApplicationContext());
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
            finish();

            // If you prefer “move to trash”, replace with:
            // removeAllInboxLabels(() -> {
            //     viewModel.addLabel(mailId, "trash", jwtToken, getApplicationContext());
            //     Toast.makeText(this, "Moved to Trash", Toast.LENGTH_SHORT).show();
            //     finish();
            // });
        });

        archiveButton.setOnClickListener(v -> {
            removeAllInboxLabels(() -> {
                viewModel.addLabel(mailId, "archive", jwtToken, getApplicationContext());
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
                    showLabelAssignmentDialog();
                    return true;
                } else if (id == R.id.menu_report_spam) {
                    removeAllInboxLabels(() -> {
                        viewModel.addLabel(mailId, "spam", jwtToken, getApplicationContext());
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
        // You may have a different filled/outline icon. Adjust as needed.
        starButton.setImageResource(isStarred ? R.drawable.ic_star_shine : R.drawable.ic_star);
    }

    private void removeLabelsSequentially(List<String> labels, int index, Runnable onComplete) {
        if (index >= labels.size()) {
            runOnUiThread(onComplete);
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
                if (!"starred".equalsIgnoreCase(label) && isInboxLabel(label)) {
                    if ("inbox".equalsIgnoreCase(label)) {
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
                        viewModel.addLabel(mailId, targetLabel, jwtToken, getApplicationContext());
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Moved to " + folderNames[which], Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLabelAssignmentDialog() {
        List<String> options = new ArrayList<>();
        options.add("Add to new label");
        for (String name : viewModel.getUserLabelNames()) {
            options.add(name);
        }
        CharSequence[] choices = options.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle("Add to label")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        showNewLabelInput();
                    } else {
                        String selectedLabel = options.get(which);
                        // IMPORTANT: addLabel requires Context as 4th arg
                        viewModel.addLabel(mailId, selectedLabel, jwtToken, getApplicationContext());
                        Toast.makeText(this, "Added to " + selectedLabel, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNewLabelInput() {
        final EditText input = new EditText(this);
        input.setHint("Label name");
        new AlertDialog.Builder(this)
                .setTitle("Create new label")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String newLabel = input.getText().toString().trim();
                    if (!newLabel.isEmpty()) {
                        // IMPORTANT: addLabel requires Context as 4th arg
                        viewModel.addLabel(mailId, newLabel, jwtToken, getApplicationContext());
                        Toast.makeText(this, "Added to " + newLabel, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
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

    // Helper: Formats ISO-8601 timestamps to "MMM d" without using java.time on API < 26.
    // Supports "2025-08-10T12:34:56Z" and offsets like "+02:00".
    private String formatIso8601ToMonthDay(String iso) throws Exception {
        if (iso == null || iso.isEmpty()) throw new IllegalArgumentException("empty timestamp");
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            // Prefer modern API on newer devices
            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(iso);
            Date date = Date.from(odt.toInstant());
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        } else {
            // Normalize offset by removing the colon in timezone if present, e.g. +02:00 -> +0200
            String normalized = iso;
            int plus = Math.max(iso.lastIndexOf('+'), iso.lastIndexOf('-'));
            if (plus > 10 && iso.length() >= plus + 6 && iso.charAt(iso.length() - 3) == ':') {
                normalized = iso.substring(0, iso.length() - 3) + iso.substring(iso.length() - 2);
            }
            // Choose pattern depending on presence of milliseconds and timezone
            boolean hasMillis = normalized.contains(".");
            boolean hasZone = normalized.endsWith("Z") || normalized.matches(".*[\\+\\-]\\d{4}$");
            String pattern;
            if (hasZone) {
                pattern = hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ";
            } else {
                pattern = hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : "yyyy-MM-dd'T'HH:mm:ss";
            }
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(true);
            if (normalized.endsWith("Z")) {
                parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            }
            Date date = parser.parse(normalized);
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        }
    }
}
