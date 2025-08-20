// MailMapper.java
package com.example.gmailish.data.mappers;

import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class MailMapper {

    private static final List<String> knownFormats = Arrays.asList(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    );

    private static Date parseDateOrNow(String value) {
        if (value == null || value.isBlank()) return new Date();
        for (String pattern : knownFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf.parse(value);
                if (d != null) return d;
            } catch (Exception ignored) {}
        }
        try {
            long epoch = Long.parseLong(value);
            return new Date(epoch);
        } catch (Exception e) {
            return new Date();
        }
    }

    private static List<String> toLabelIdList(JSONArray array) {
        if (array == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String v = array.optString(i, null);
            if (v != null && !v.isBlank()) out.add(v);
        }
        return out;
    }

    public static MailEntity mailEntityFromJson(JSONObject json) {
        return new MailEntity(
                json.optString("id"),
                json.optString("senderId"),
                json.optString("senderName"),
                json.optString("recipientId"),
                json.optString("recipientName"),
                json.optString("recipientEmail"),
                json.optString("subject"),
                json.optString("content"),
                parseDateOrNow(json.optString("timestamp", null)),
                json.optString("ownerId"),
                json.optBoolean("read", false),
                json.optBoolean("starred", false)
        );
    }

    public static List<String> labelIdsFromJson(JSONObject json) {
        JSONArray arr = json.has("labels") ? json.optJSONArray("labels") : null;
        return toLabelIdList(arr);
    }

    public static List<MailLabelCrossRef> crossRefsForMail(String mailId, List<String> labelIds) {
        List<MailLabelCrossRef> out = new ArrayList<>(labelIds.size());
        for (String lid : labelIds) out.add(new MailLabelCrossRef(mailId, lid));
        return out;
    }

    public static LabelEntity labelEntityFromJson(JSONObject json) {
        String id = json.optString("id");
        String ownerId = json.optString("ownerId");
        String name = json.optString("name", id);
        return new LabelEntity(id, ownerId, name);
    }

    private MailMapper() {}
}
