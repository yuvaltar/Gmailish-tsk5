package com.example.gmailish.ui.inbox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gmailish.R;
import com.example.gmailish.mail.MailViewActivity;
import com.example.gmailish.model.Email;

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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EmailAdapter extends RecyclerView.Adapter<EmailAdapter.EmailViewHolder> {

    private final List<Email> emailList = new ArrayList<>();

    // Networking for star toggle
    private final OkHttpClient http = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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
            sender     = view.findViewById(R.id.sender);
            subject    = view.findViewById(R.id.subject);
            content    = view.findViewById(R.id.content);
            timestamp  = view.findViewById(R.id.timestamp);
            starIcon   = view.findViewById(R.id.starIcon);
        }
    }

    @NonNull
    @Override
    public EmailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.email_item, parent, false);
        return new EmailViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EmailViewHolder holder, int position) {
        Email email = emailList.get(position);

        // Bind basics
        String senderName = email.senderName != null ? email.senderName : "";
        holder.sender.setText(senderName);
        holder.subject.setText(email.subject != null ? email.subject : "");
        holder.content.setText(email.content != null ? email.content : "");

        // Format timestamp: today -> HH:mm, else -> MMM d
        String prettyTs = formatListTimestamp(email.timestamp);
        holder.timestamp.setText(prettyTs);

        // First letter avatar
        holder.senderIcon.setText(senderName.isEmpty()
                ? "?"
                : senderName.substring(0, 1).toUpperCase());

        // Read/unread visual
        holder.itemView.setAlpha(email.read ? 0.6f : 1.0f);

        // Star icon (match MailView visuals)
        setStarIcon(holder.starIcon, email.starred);

        // Toggle star from list (optimistic UI + server)
        holder.starIcon.setOnClickListener(v -> {
            boolean newState = !email.starred;

            // optimistic UI update
            email.starred = newState;
            int p = holder.getAdapterPosition();
            if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);

            // persist to backend via label add/remove
            Context ctx = v.getContext().getApplicationContext();
            SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
            String token = prefs.getString("jwt", null);
            if (token != null) {
                patchLabel(token, email.id, "starred", !newState); // remove when turning off
            }
        });

        // Open message -> optimistic read + launch detail
        holder.itemView.setOnClickListener(v -> {
            if (!email.read) {
                email.read = true;
                int p = holder.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
            }
            Intent intent = new Intent(v.getContext(), MailViewActivity.class);
            intent.putExtra("mailId", email.id); // id is String
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return emailList.size();
    }

    private void setStarIcon(ImageView iv, boolean starred) {
        // Same icons as MailView
        iv.setImageResource(starred ? R.drawable.ic_star_shine : R.drawable.ic_star);
    }

    /** PATCH /api/mails/:id/label with either add or remove action for "starred" */
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
                    // Keep optimistic UI; optionally reload on failure
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    response.close(); // no UI change needed
                }
            });
        } catch (Exception ignored) {}
    }

    /* =========================
       Timestamp formatting
       ========================= */

    private String formatListTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // ISO-8601 → ZonedDateTime in local zone
                OffsetDateTime odt = OffsetDateTime.parse(raw);
                ZonedDateTime zdt = odt.atZoneSameInstant(ZoneId.systemDefault());

                boolean isToday = zdt.toLocalDate().isEqual(java.time.LocalDate.now(ZoneId.systemDefault()));
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(isToday ? "HH:mm" : "MMM d", Locale.getDefault());
                return zdt.format(fmt);
            } else {
                // Fallback parser tolerant to: Z or ±hh:mm, with/without millis
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
            return raw; // if anything fails, show raw string
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
