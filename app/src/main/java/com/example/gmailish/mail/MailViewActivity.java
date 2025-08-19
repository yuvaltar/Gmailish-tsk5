package com.example.gmailish.mail;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import java.util.Locale;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MailViewActivity extends AppCompatActivity {

    private TextView senderText, recipientText, subjectText, contentText, timestampText, senderIcon;
    private ImageButton replyButton, forwardButton, starButton, deleteButton, archiveButton, menuButton, backButton;
    private MailViewModel viewModel;
    private String mailId;
    private boolean isStarred;
    private String jwtToken;
    private JSONArray currentLabels; // from /mails/:id
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_view);

        senderText = findViewById(R.id.senderText);
        recipientText = findViewById(R.id.recipientText);
        subjectText = findViewById(R.id.subjectText);
        contentText = findViewById(R.id.contentText);
        timestampText = findViewById(R.id.timestampText);
        senderIcon = findViewById(R.id.senderIcon);

        replyButton   = findViewById(R.id.replyButton);
        forwardButton = findViewById(R.id.forwardButton);
        starButton    = findViewById(R.id.starButton);
        deleteButton  = findViewById(R.id.deleteButton);
        archiveButton = findViewById(R.id.archiveButton);
        menuButton    = findViewById(R.id.menuButton);
        backButton    = findViewById(R.id.backButton);

        viewModel = new ViewModelProvider(this).get(MailViewModel.class);
        viewModel.init(getApplicationContext());

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        jwtToken = prefs.getString("jwt", null);
        mailId   = getIntent().getStringExtra("mailId");

        preloadUserLabelsFromPrefs(); // lets us show labels instantly

        viewModel.mailData.observe(this, mail -> {
            if (mail == null) return;

            senderText.setText(mail.optString("senderName"));
            recipientText.setText("To: " + mail.optString("recipientName") + " <" + mail.optString("recipientEmail") + ">");
            subjectText.setText(mail.optString("subject"));
            contentText.setText(mail.optString("content"));

            String rawTime = mail.optString("timestamp");
            try { timestampText.setText(formatIso8601ToMonthDay(rawTime)); }
            catch (Exception e) { timestampText.setText(rawTime); }

            String senderName = mail.optString("senderName");
            if (senderName != null && !senderName.isEmpty()) {
                senderIcon.setText(senderName.substring(0, 1).toUpperCase());
            }

            currentLabels = mail.optJSONArray("labels");
            isStarred = currentLabels != null && currentLabels.toString().toLowerCase().contains("starred");
            updateStarIcon();
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
                    showLabelPickerWithChecks();   // ← NEW: checkbox picker
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

    /* ---------- Label picker with checkboxes ---------- */

    private void showLabelPickerWithChecks() {
        // Build the list of user labels
        List<String> labels = viewModel.getUserLabelNames();
        if (labels == null) labels = new ArrayList<>();
        final String[] items = labels.toArray(new String[0]);

        // Current check state (pre-check labels already on this mail)
        final boolean[] checked = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            checked[i] = hasLabelApplied(items[i]);
        }
        // Keep a copy to compute diffs at the end
        final boolean[] original = checked.clone();

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Label")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Done", (dialog, which) -> {
                    // Diff & apply changes sequentially
                    List<Toggle> ops = new ArrayList<>();
                    for (int i = 0; i < items.length; i++) {
                        if (checked[i] != original[i]) {
                            ops.add(new Toggle(items[i], checked[i])); // true = add, false = remove
                        }
                    }
                    if (ops.isEmpty()) return;
                    applyTogglesSequentially(ops, 0);
                })
                .setNeutralButton("New label", (d, w) -> promptNewLabelAndReopen());
        b.show();
    }

    private boolean hasLabelApplied(String label) {
        if (currentLabels == null) return false;
        for (int i = 0; i < currentLabels.length(); i++) {
            String s = currentLabels.optString(i, "");
            if (s.equalsIgnoreCase(label)) return true;
        }
        // “inbox” normalization
        if ("primary".equalsIgnoreCase(label)) {
            for (int i = 0; i < currentLabels.length(); i++) {
                if ("inbox".equalsIgnoreCase(currentLabels.optString(i))) return true;
            }
        }
        return false;
    }

    private static class Toggle {
        final String label; final boolean add;
        Toggle(String l, boolean a) { label = l; add = a; }
    }

    private void applyTogglesSequentially(List<Toggle> ops, int i) {
        if (i >= ops.size()) {
            Toast.makeText(this, "Labels updated", Toast.LENGTH_SHORT).show();
            // Refresh mail from server to get updated label list
            viewModel.fetchMailById(mailId, jwtToken);
            return;
        }
        Toggle t = ops.get(i);
        if (t.add) {
            viewModel.addLabel(mailId, t.label, jwtToken, getApplicationContext());
        } else {
            viewModel.removeLabel(mailId, t.label, jwtToken, getApplicationContext());
        }
        // Chain next op after a tiny delay to avoid hammering server (optional)
        senderIcon.postDelayed(() -> applyTogglesSequentially(ops, i + 1), 40);
    }

    private void promptNewLabelAndReopen() {
        final EditText input = new EditText(this);
        input.setHint("Label name");
        new AlertDialog.Builder(this)
                .setTitle("Create new label")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createLabelOnServer(name, () -> {
                        // Cache it locally so it appears next time
                        cacheLabelName(name);
                        // Re-open picker and pre-select the new label
                        // (also apply the label to this mail right away)
                        viewModel.addLabel(mailId, name, jwtToken, getApplicationContext());
                        showLabelPickerWithChecks();
                    });
                })
                .show();
    }

    private void createLabelOnServer(String name, Runnable onOk) {
        try {
            JSONObject obj = new JSONObject().put("name", name);
            RequestBody body = RequestBody.create(JSON, obj.toString());
            Request req = new Request.Builder()
                    .url("http://10.0.2.2:3000/api/labels")
                    .post(body)
                    .header("Authorization", "Bearer " + jwtToken)
                    .build();
            new OkHttpClient().newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(MailViewActivity.this,
                            "Create label failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                    response.close();
                    runOnUiThread(onOk);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Create label error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void cacheLabelName(String name) {
        try {
            SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
            String s = p.getString("cached_label_names", "[]");
            JSONArray arr = new JSONArray(s);
            boolean exists = false;
            for (int i = 0; i < arr.length(); i++) {
                if (name.equalsIgnoreCase(arr.optString(i))) { exists = true; break; }
            }
            if (!exists) {
                arr.put(name);
                p.edit().putString("cached_label_names", arr.toString()).apply();
                // push into VM cache too
                List<JSONObject> raw = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = new JSONObject();
                    o.put("name", arr.optString(i));
                    raw.add(o);
                }
                viewModel.setUserLabels(raw);
            }
        } catch (Exception ignored) {}
    }

    /* ---------- Existing helpers (unchanged) ---------- */

    private void updateStarIcon() {
        starButton.setImageResource(isStarred ? R.drawable.ic_star_shine : R.drawable.ic_star);
    }

    private void preloadUserLabelsFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String json = prefs.getString("cached_label_names", "[]");
            JSONArray arr = new JSONArray(json);
            List<JSONObject> raw = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.optString(i, null);
                if (name != null) raw.add(new JSONObject().put("name", name));
            }
            viewModel.setUserLabels(raw);
        } catch (Exception ignored) {}
    }

    private void removeLabelsSequentially(List<String> labels, int index, Runnable onComplete) {
        if (index >= labels.size()) { runOnUiThread(onComplete); return; }
        String label = labels.get(index);
        viewModel.removeLabelWithCallback(mailId, label, jwtToken,
                () -> removeLabelsSequentially(labels, index + 1, onComplete));
    }

    private void removeAllInboxLabels(Runnable onComplete) {
        if (currentLabels == null) { onComplete.run(); return; }
        List<String> labelsToRemove = new ArrayList<>();
        for (int i = 0; i < currentLabels.length(); i++) {
            String label = currentLabels.optString(i, "");
            if (!"starred".equalsIgnoreCase(label) && isInboxLabel(label)) {
                labelsToRemove.add("inbox".equalsIgnoreCase(label) ? "primary" : label);
            }
        }
        removeLabelsSequentially(labelsToRemove, 0, onComplete);
    }

    private void showMoveToDialog() {
        String[] folderNames = {"Primary", "Promotions", "Social", "Updates", "Spam", "Trash", "Drafts", "Starred", "Important"};
        String[] labelValues = {"primary", "promotions", "social", "updates", "spam", "trash", "drafts", "starred", "important"};
        new AlertDialog.Builder(this)
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

    private boolean isInboxLabel(String label) {
        return label.equalsIgnoreCase("primary") || label.equalsIgnoreCase("inbox") ||
                label.equalsIgnoreCase("promotions") || label.equalsIgnoreCase("social") ||
                label.equalsIgnoreCase("updates") || label.equalsIgnoreCase("trash") ||
                label.equalsIgnoreCase("drafts") || label.equalsIgnoreCase("spam") ||
                label.equalsIgnoreCase("archive") || label.equalsIgnoreCase("important");
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
                normalized = iso.substring(0, iso.length() - 3) + iso.substring(iso.length() - 2);
            }
            boolean hasMillis = normalized.contains(".");
            boolean hasZone = normalized.endsWith("Z") || normalized.matches(".*[\\+\\-]\\d{4}$");
            String pattern = hasZone
                    ? (hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ")
                    : (hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : "yyyy-MM-dd'T'HH:mm:ss");
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(true);
            if (normalized.endsWith("Z")) parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(normalized);
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        }
    }
}
