package com.example.gmailish.mail;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
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
                bindMailToViews(mail);
            }
        });

        viewModel.errorMessage.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        );

        // OFFLINE-FIRST load and then refresh if online
        viewModel.loadMailDetail(getApplicationContext(), mailId, jwtToken);
        // Mark as read (network + local)
        if (jwtToken != null && !jwtToken.isEmpty()) {
            viewModel.markAsRead(mailId, jwtToken);
        }

        replyButton.setOnClickListener(v ->
                Toast.makeText(this, "Reply (not implemented yet)", Toast.LENGTH_SHORT).show()
        );

        forwardButton.setOnClickListener(v ->
                Toast.makeText(this, "Forward (not implemented yet)", Toast.LENGTH_SHORT).show()
        );

        starButton.setOnClickListener(v -> {
            boolean willBeStarred = !isStarred;
            viewModel.addOrRemoveLabel(mailId, "starred", jwtToken, willBeStarred, getApplicationContext());
            isStarred = willBeStarred;
            updateStarIcon();
        });

        deleteButton.setOnClickListener(v -> {
            viewModel.deleteMail(mailId, jwtToken, getApplicationContext());
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
            finish();
        });

        archiveButton.setOnClickListener(v -> {
            // Use moveToLabel flow directly to keep offline + pending consistent
            viewModel.moveToLabelOfflineFirst(mailId, "archive", jwtToken, getApplicationContext(), () -> {
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
                    viewModel.moveToLabelOfflineFirst(mailId, "spam", jwtToken, getApplicationContext(), () -> {
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

    private void bindMailToViews(JSONObject mail) {
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
        } else {
            senderIcon.setText("");
        }

        currentLabels = mail.optJSONArray("labels");
        if (currentLabels == null) currentLabels = new JSONArray();
        isStarred = containsLabel(currentLabels, "starred");
        updateStarIcon();
    }

    private boolean containsLabel(JSONArray array, String label) {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) {
            if (label.equalsIgnoreCase(array.optString(i))) return true;
        }
        return false;
    }

    private void updateStarIcon() {
        starButton.setImageResource(isStarred ? R.drawable.ic_star_shine : R.drawable.ic_star);
    }

    private void showMoveToDialog() {
        String[] folderNames = {"Primary", "Promotions", "Social", "Updates", "Spam", "Trash", "Drafts", "Starred", "Important", "Archive"};
        String[] labelValues = {"primary", "promotions", "social", "updates", "spam", "trash", "drafts", "starred", "important", "archive"};
        new AlertDialog.Builder(MailViewActivity.this)
                .setTitle("Move to")
                .setItems(folderNames, (dialog, which) -> {
                    String targetLabel = labelValues[which];
                    viewModel.moveToLabelOfflineFirst(mailId, targetLabel, jwtToken, getApplicationContext(), () -> {
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
                        // For "label as", keep current inbox labels and add another tag
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
                        viewModel.addLabel(mailId, newLabel, jwtToken, getApplicationContext());
                        Toast.makeText(this, "Added to " + newLabel, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String formatIso8601ToMonthDay(String iso) throws Exception {
        if (iso == null || iso.isEmpty()) throw new IllegalArgumentException("empty timestamp");
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(iso);
            Date date = Date.from(odt.toInstant());
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        } else {
            String normalized = iso;
            int plus = Math.max(iso.lastIndexOf('+'), iso.lastIndexOf('-'));
            if (plus > 10 && iso.length() >= plus + 6 && iso.charAt(iso.length() - 3) == ':') {
                // Normalize timezone like +03:00 -> +0300
                normalized = iso.substring(0, iso.length() - 3) + iso.substring(iso.length() - 2);
            }
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
