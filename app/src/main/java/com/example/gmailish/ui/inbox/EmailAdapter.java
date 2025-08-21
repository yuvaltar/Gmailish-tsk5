package com.example.gmailish.ui.inbox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.gmailish.data.db.AppDbProvider;
import com.example.gmailish.data.db.AppDatabase;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gmailish.R;
import com.example.gmailish.mail.MailViewActivity;
import com.example.gmailish.model.Email;
import com.example.gmailish.ui.compose.ComposeActivity;

import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EmailAdapter extends RecyclerView.Adapter<EmailAdapter.EmailViewHolder> {

    private static final String TAG = "EmailAdapter";

    private final List<Email> emailList = new ArrayList<>();
    private final OkHttpClient http = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Which label we are currently showing (e.g., "inbox", "starred", "drafts").
    private String currentLabel = null;

    /** Call this from your fragment/activity when switching lists, e.g. setCurrentLabel("drafts"). */
    public void setCurrentLabel(String label) {
        this.currentLabel = label != null ? label.toLowerCase(Locale.ROOT) : null;
        notifyDataSetChanged();
    }

    public void updateData(List<Email> newEmails) {
        emailList.clear();
        if (newEmails != null) {
            emailList.addAll(newEmails);
        }
        notifyDataSetChanged();
    }

    static class EmailViewHolder extends RecyclerView.ViewHolder {
        final TextView senderIcon, sender, subject, content, timestamp;
        final ImageView starIcon;

        EmailViewHolder(@NonNull View view) {
            super(view);
            senderIcon = view.findViewById(R.id.senderIcon);
            sender = view.findViewById(R.id.sender);
            subject = view.findViewById(R.id.subject);
            content = view.findViewById(R.id.content);
            timestamp = view.findViewById(R.id.timestamp);
            starIcon = view.findViewById(R.id.starIcon);
        }
    }

    @NonNull
    @Override
    public EmailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.email_item, parent, false);
        return new EmailViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EmailViewHolder holder, int position) {
        Email email = emailList.get(position);

        String senderName = email.senderName != null ? email.senderName : "";
        holder.sender.setText(senderName);

        boolean viewingDrafts = "drafts".equalsIgnoreCase(currentLabel);
        holder.subject.setText(viewingDrafts
                ? "[Draft] " + (email.subject != null ? email.subject : "")
                : (email.subject != null ? email.subject : ""));

        holder.content.setText(email.content != null ? email.content : "");

        String prettyTs = formatListTimestamp(email.timestamp);
        holder.timestamp.setText(prettyTs);

        holder.senderIcon.setText(senderName.isEmpty()
                ? "?"
                : senderName.substring(0, 1).toUpperCase(Locale.ROOT));

        holder.itemView.setAlpha(email.read ? 0.6f : 1.0f);
        setStarIcon(holder.starIcon, email.starred);

        if (viewingDrafts) {
            // Don’t allow starring in Drafts
            holder.starIcon.setAlpha(0.3f);
            holder.starIcon.setOnClickListener(null);
        } else {
            holder.starIcon.setAlpha(1f);
            holder.starIcon.setOnClickListener(v -> {
                boolean newState = !email.starred;
                email.starred = newState;
                int p = holder.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);

                Context app = v.getContext().getApplicationContext();
                SharedPreferences prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String token = prefs.getString("jwt", null);

                String labelId = "starred";
                LocalLabelActions.applyStarLocally(app, email.id, newState, labelId);

                if (token != null) {
                    patchLabel(token, email.id, labelId, !newState);
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (viewingDrafts) {
                // Open Compose to continue editing this draft
                Intent i = new Intent(v.getContext(), ComposeActivity.class);
                i.putExtra("EXTRA_MODE", "edit_draft");
                i.putExtra("EXTRA_DRAFT_ID", email.id);
                v.getContext().startActivity(i);
                return;
            }

            // Normal flow: open the mail viewer
            if (!email.read) {
                email.read = true;
                int p = holder.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
            }
            Intent intent = new Intent(v.getContext(), MailViewActivity.class);
            intent.putExtra("mailId", email.id);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return emailList.size();
    }

    private void setStarIcon(ImageView iv, boolean starred) {
        iv.setImageResource(starred ? R.drawable.ic_star_shine : R.drawable.ic_star);
    }

    private void patchLabel(String token, String mailId, String label, boolean remove) {
        try {
            JSONObject json = new JSONObject();
            json.put("label", label);
            if (remove) json.put("action", "remove");

            RequestBody body = RequestBody.create(JSON, json.toString());
            Request req = new Request.Builder()
                    .url("http://10.0.2.2:3000/api/mails/" + mailId + "/label")
                    .patch(body)
                    .header("Authorization", "Bearer " + token)
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.w(TAG, "patchLabel failed: " + e.getMessage());
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "patchLabel exception: " + e.getMessage());
        }
    }

    /** Local-only helpers for toggling the "starred" label and flag. */
    static final class LocalLabelActions {
        static AppDatabase getDb(Context ctx) {
            return AppDbProvider.get(ctx.getApplicationContext());
        }

        static void applyStarLocally(Context ctx, String mailId, boolean starred, String labelIdRaw) {
            String labelId = normalizeLabelId(labelIdRaw);
            Log.d(TAG, "applyStarLocally: mailId=" + mailId + " starred=" + starred + " labelId=" + labelId);

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    var db = getDb(ctx);
                    var labelDao = db.labelDao();
                    var mailLabelDao = db.mailLabelDao();
                    var mailDao = db.mailDao();

                    mailDao.setStarred(mailId, starred);

                    if (starred) {
                        labelDao.upsert(new com.example.gmailish.data.entity.LabelEntity(labelId, null, labelId));
                        mailLabelDao.add(new com.example.gmailish.data.entity.MailLabelCrossRef(mailId, labelId));
                    } else {
                        mailLabelDao.remove(mailId, labelId);
                    }

                    int count = mailLabelDao.getMailsForLabelSync(labelId).size();
                    Log.d(TAG, "applyStarLocally: label '" + labelId + "' now has mails=" + count);
                } catch (Throwable t) {
                    Log.e(TAG, "applyStarLocally error: " + t.getMessage());
                }
            });
        }

        static String normalizeLabelId(String id) {
            if (id == null) return null;
            if ("inbox".equalsIgnoreCase(id)) return "primary";
            return id.toLowerCase(Locale.ROOT);
        }
    }

    /* =========================
       Timestamp formatting
       ========================= */


    private String formatListTimestamp(String raw) {

        if (raw == null || raw.isEmpty()) return "";
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                OffsetDateTime odt = OffsetDateTime.parse(raw);
                ZonedDateTime zdt = odt.atZoneSameInstant(ZoneId.systemDefault());

                boolean isToday = zdt.toLocalDate().isEqual(java.time.LocalDate.now(ZoneId.systemDefault()));
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(isToday ? "HH:mm" : "MMM d", Locale.getDefault());
                return zdt.format(fmt);
            } else {
                Date date = parseLegacyIso(raw);
                if (date == null) return raw;

                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);

                java.util.Calendar now = java.util.Calendar.getInstance();
                boolean isToday =
                        cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                                cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);

                return new SimpleDateFormat(isToday ? "HH:mm" : "MMM d", Locale.getDefault()).format(date);
            }
        } catch (Throwable t) {
            return raw;
        }
    }

    private Date parseLegacyIso(String iso) {
        try {
            String normalized = iso;
            int plus = Math.max(iso.lastIndexOf('+'), iso.lastIndexOf('-'));
            if (plus > 10 && iso.length() >= plus + 6 && iso.charAt(iso.length() - 3) == ':') {
                // "+02:00" -> "+0200"
                normalized = iso.substring(0, iso.length() - 3) + iso.substring(iso.length() - 2);
            }
            boolean hasMillis = normalized.contains(".");
            boolean hasZone = normalized.endsWith("Z") || normalized.matches(".*[\\+\\-]\\d{4}$");
            String pattern = hasZone
                    ? (hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ")
                    : (hasMillis ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : "yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(true);
            if (normalized.endsWith("Z")) {
                parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            }
            return parser.parse(normalized);
        } catch (ParseException e) {
            return null;
        }
    }
}
