package com.example.gmailish.mail;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import android.widget.LinearLayout;
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


import dagger.hilt.android.AndroidEntryPoint;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * MailViewActivity – reply now fills the real sender email and the reply/forward boxes are clickable.
 */
@AndroidEntryPoint
public class MailViewActivity extends AppCompatActivity {

    private TextView senderText, recipientText, subjectText, contentText, timestampText, senderIcon;
    private ImageButton replyButton, forwardButton, starButton, deleteButton, archiveButton, menuButton, backButton;

    // NEW: containers for whole boxes
    private LinearLayout replyBox, forwardBox;

    private MailViewModel viewModel;

    private String mailId;
    private boolean isStarred;
    private String jwtToken;

    private JSONArray currentLabels;   // from /mails/:id
    private JSONObject currentMail;    // keep whole mail for reply/forward

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mail_view);

        senderText    = findViewById(R.id.senderText);
        recipientText = findViewById(R.id.recipientText);
        subjectText   = findViewById(R.id.subjectText);
        contentText   = findViewById(R.id.contentText);
        timestampText = findViewById(R.id.timestampText);
        senderIcon    = findViewById(R.id.senderIcon);

        replyButton   = findViewById(R.id.replyButton);
        forwardButton = findViewById(R.id.forwardButton);
        starButton    = findViewById(R.id.starButton);
        deleteButton  = findViewById(R.id.deleteButton);
        archiveButton = findViewById(R.id.archiveButton);
        menuButton    = findViewById(R.id.menuButton);
        backButton    = findViewById(R.id.backButton);

        // NEW: grab the whole clickable boxes
        replyBox      = findViewById(R.id.replyBox);
        forwardBox    = findViewById(R.id.forwardBox);

        viewModel = new ViewModelProvider(this).get(MailViewModel.class);


        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        jwtToken = prefs.getString("jwt", null);
        mailId   = getIntent().getStringExtra("mailId");

        preloadUserLabelsFromPrefs();

        viewModel.mailData.observe(this, mail -> {
            if (mail == null){
              bindMailToViews(mail);
              return;
            }

            currentMail = mail;

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
            isStarred = currentLabels != null && currentLabels.toString().toLowerCase(Locale.ROOT).contains("starred");
            updateStarIcon();
        });

        viewModel.errorMessage.observe(this, msg ->

                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
      
       // OFFLINE-FIRST load and then refresh if online
        viewModel.loadMailDetail(getApplicationContext(), mailId, jwtToken);

        viewModel.fetchMailById(mailId, jwtToken);
      // Mark as read (network + local)
        if (jwtToken != null && !jwtToken.isEmpty()) {
            viewModel.markAsRead(mailId, jwtToken);
        }

        /* ----------------- Reply / Forward ----------------- */

        // Icon clicks:
        replyButton.setOnClickListener(v -> replyToSender());
        forwardButton.setOnClickListener(v -> openForward());

        // NEW: whole-box clicks (same actions):
        replyBox.setOnClickListener(v -> replyToSender());
        forwardBox.setOnClickListener(v -> openForward());

        /* --------------------------------------------------- */

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
                    showLabelPickerWithChecks();
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

    /* ================= Reply helpers ================= */

    private void openForward() {
        if (currentMail == null) return;
        Intent i = new Intent(this, com.example.gmailish.ui.compose.ComposeActivity.class);
        i.putExtra("EXTRA_MODE", "forward");
        i.putExtra("EXTRA_SUBJECT", prefixIfNeeded(subjectText.getText().toString(), "Fwd: "));
        i.putExtra("EXTRA_BODY", buildForwardBody()); // leave To empty
        startActivity(i);
    }

    private void replyToSender() {
        if (currentMail == null) return;

        // 1) Try to extract directly from the message JSON
        String email = extractSenderEmail(currentMail);
        if (!email.isEmpty()) {
            openComposeReply(email);
            return;
        }

        // 2) Try to resolve by senderId from the API
        String senderId = currentMail.optString("senderId", "");
        if (!senderId.isEmpty()) {
            resolveSenderEmailFromServer(senderId, resolved -> {
                String finalEmail = (resolved != null && !resolved.isEmpty())
                        ? resolved
                        : fallbackEmailFromName(currentMail); // 3) fallback to name@gmailish.com
                if (finalEmail.isEmpty()) {
                    Toast.makeText(this, "Sender email unavailable", Toast.LENGTH_SHORT).show();
                } else {
                    openComposeReply(finalEmail);
                }
            });
            return;
        }

        // 3) No senderId? just fallback to name@gmailish.com
        String fb = fallbackEmailFromName(currentMail);
        if (fb.isEmpty()) {
            Toast.makeText(this, "Sender email unavailable", Toast.LENGTH_SHORT).show();
        } else {
            openComposeReply(fb);
        }
    }

    private void openComposeReply(String email) {
        Intent i = new Intent(this, com.example.gmailish.ui.compose.ComposeActivity.class);
        i.putExtra("EXTRA_MODE", "reply");
        i.putExtra("EXTRA_TO", email); // <-- email only
        i.putExtra("EXTRA_SUBJECT", prefixIfNeeded(subjectText.getText().toString(), "Re: "));
        startActivity(i);
    }

    /** Pull the best candidate email out of the message JSON (no fallbacks). */
    private String extractSenderEmail(JSONObject mail) {
        String[] fields = new String[] {
                "senderEmail", "fromEmail", "emailFrom", "replyTo", "from_address"
        };
        for (String f : fields) {
            String v = mail.optString(f, "");
            if (containsEmail(v)) return stripAngle(v);
        }
        // Common combined header e.g. "Name <user@host>"
        String[] combo = new String[] { "from", "sender", "senderDisplay" };
        for (String f : combo) {
            String v = mail.optString(f, "");
            if (containsEmail(v)) return stripAngle(v);
        }
        return "";
    }

    /** GET /api/users/{senderId} -> use 'email' (fallback 'username'). */
    private void resolveSenderEmailFromServer(String senderId, EmailResolver cb) {
        try {
            SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
            String token = p.getString("jwt", null);
            if (token == null) { cb.onResolved(""); return; }

            Request req = new Request.Builder()
                    .url("http://10.0.2.2:3000/api/users/" + senderId)
                    .header("Authorization", "Bearer " + token)
                    .build();

            new OkHttpClient().newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException e) {
                    runOnUiThread(() -> cb.onResolved(""));
                }
                @Override public void onResponse(Call call, Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    response.close();
                    String out = "";
                    try {
                        JSONObject o = new JSONObject(body);
                        String email = o.optString("email", "");
                        String username = o.optString("username", "");
                        if (containsEmail(email)) out = email;
                        else if (!username.isEmpty()) out = username; // last resort
                    } catch (Exception ignored) {}
                    final String finalOut = out;
                    runOnUiThread(() -> cb.onResolved(finalOut));
                }
            });
        } catch (Exception e) {
            cb.onResolved("");
        }
    }

    /** Make an email from the best name/username we can find, like "yuvaltest@gmailish.com". */
    private String fallbackEmailFromName(JSONObject mail) {
        String name = firstNonEmpty(
                mail.optString("senderUsername", ""),
                mail.optString("senderName", ""),
                mail.optString("sender", "")
        );
        if (name == null) name = "";
        if (containsEmail(name)) return stripAngle(name);

        String local = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", ".")        // spaces -> dots
                .replaceAll("[^a-z0-9._-]", ""); // drop anything unsafe
        if (local.isEmpty()) return "";
        return local + "@gmailish.com";
    }

    private String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private interface EmailResolver { void onResolved(String email); }

    private boolean containsEmail(String s) {
        return s != null && s.contains("@") && s.indexOf('@') > 0;
    }
    private String stripAngle(String s) {
        if (s == null) return "";
        int lt = s.indexOf('<'), gt = s.indexOf('>');
        if (lt >= 0 && gt > lt) return s.substring(lt + 1, gt).trim();
        return s.trim();
    }
    private String prefixIfNeeded(String subject, String prefix) {
        if (subject == null) subject = "";
        String p = prefix.trim().toLowerCase(Locale.ROOT);
        return subject.toLowerCase(Locale.ROOT).startsWith(p) ? subject : prefix + subject;
    }
    private String buildForwardBody() {
        CharSequence cs = contentText.getText();
        return cs != null ? cs.toString() : "";
    }

    /* ================= Label picker etc. (unchanged) ================= */

    private void showLabelPickerWithChecks() {
        List<String> labels = viewModel.getUserLabelNames();
        if (labels == null) labels = new ArrayList<>();
        final String[] items = labels.toArray(new String[0]);

        final boolean[] checked = new boolean[items.length];
        for (int i = 0; i < items.length; i++) checked[i] = hasLabelApplied(items[i]);
        final boolean[] original = checked.clone();

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Label")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Done", (dialog, which) -> {
                    List<Toggle> ops = new ArrayList<>();
                    for (int i = 0; i < items.length; i++) {
                        if (checked[i] != original[i]) ops.add(new Toggle(items[i], checked[i]));
                    }
                    if (!ops.isEmpty()) applyTogglesSequentially(ops, 0);
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
        if ("primary".equalsIgnoreCase(label)) {
            for (int i = 0; i < currentLabels.length(); i++) {
                if ("inbox".equalsIgnoreCase(currentLabels.optString(i))) return true;
            }
        }
        return false;
    }

    private static class Toggle { final String label; final boolean add; Toggle(String l, boolean a){label=l;add=a;} }

    private void applyTogglesSequentially(List<Toggle> ops, int i) {
        if (i >= ops.size()) {
            Toast.makeText(this, "Labels updated", Toast.LENGTH_SHORT).show();
            viewModel.fetchMailById(mailId, jwtToken);
            return;
        }
        Toggle t = ops.get(i);
        if (t.add) viewModel.addLabel(mailId, t.label, jwtToken, getApplicationContext());
        else       viewModel.removeLabel(mailId, t.label, jwtToken, getApplicationContext());
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
                    if (name.isEmpty()) { Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show(); return; }
                    createLabelOnServer(name, () -> {
                        cacheLabelName(name);
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
            new OkHttpClient().newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(MailViewActivity.this,"Create label failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(Call call, Response response) throws java.io.IOException {
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
            for (int i = 0; i < arr.length(); i++) if (name.equalsIgnoreCase(arr.optString(i))) { exists = true; break; }
            if (!exists) {
                arr.put(name);
                p.edit().putString("cached_label_names", arr.toString()).apply();
                List<JSONObject> raw = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) raw.add(new JSONObject().put("name", arr.optString(i)));
                viewModel.setUserLabels(raw);
            }
        } catch (Exception ignored) {}
    }

    /* ================= Misc helpers ================= */

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
                // Normalize timezone like +03:00 -> +0300
                normalized = iso.substring(0, iso.length() - 3) + iso.substring(iso.length() - 2);
            }
            boolean hasMillis = normalized.contains(".");
            boolean hasZone = normalized.endsWith("Z") || normalized.matches(".*[\\+\\-]\\d{4}$");
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(
                    hasZone ? (hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ")
                            : (hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : "yyyy-MM-dd'T'HH:mm:ss"),
                    Locale.US
            );
            parser.setLenient(true);
            if (normalized.endsWith("Z")) parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(normalized);
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        }
    }
}
