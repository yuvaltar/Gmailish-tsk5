package com.example.gmailish.data.repository;

import android.util.Log;

import com.example.gmailish.data.dao.LabelDao;
import com.example.gmailish.data.dao.MailDao;
import com.example.gmailish.data.dao.MailLabelDao;
import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public class MailRepository {

    private static final String TAG = "MailRepo";

    private static final String LABEL_DRAFTS = "drafts";
    private static final String LABEL_SENT   = "sent";
    private static final String LABEL_OUTBOX = "outbox";

    private final MailDao mailDao;
    private final LabelDao labelDao;
    private final MailLabelDao mailLabelDao;

    public MailRepository(MailDao mailDao, LabelDao labelDao, MailLabelDao mailLabelDao) {
        this.mailDao = mailDao;
        this.labelDao = labelDao;
        this.mailLabelDao = mailLabelDao;
    }

    // -------- Reads (blocking; call on background thread) --------
    public List<MailEntity> getInbox(String ownerId) {
        return mailDao.getMailsByOwnerSync(ownerId);
    }

    public List<MailEntity> search(String ownerId, String likeQuery) {
        return mailDao.searchMailsSync(ownerId, likeQuery);
    }

    public List<MailEntity> getByLabel(String labelId) {
        return mailLabelDao.getMailsForLabelSync(labelId);
    }

    public List<MailEntity> getStarred(String ownerId) {
        return mailDao.getStarredByOwnerSync(ownerId);
    }

    // NEW: single-mail getters for offline detail
    public MailEntity getByIdSync(String mailId) {
        return mailDao.getByIdSync(mailId);
    }

    public List<String> getLabelsForMailSync(String mailId) {
        return mailLabelDao.getLabelsForMailSync(mailId);
    }

    // Backward-compatible ISO-8601 format (UTC) for Date -> String (API 24 safe)
    private static String dateToIso8601(Date date) {
        if (date == null) return null;
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            return df.format(date);
        } catch (Throwable t) {
            return date.toString();
        }
    }

    // Helper to present as JSONObject for UI (offline)
    public JSONObject buildMailJson(MailEntity m, List<String> labels) {
        if (m == null) return null;
        try {
            JSONObject json = new JSONObject();
            json.put("id", m.getId());
            json.put("senderId", m.getSenderId());
            json.put("senderName", m.getSenderName());
            json.put("recipientId", m.getRecipientId());
            json.put("recipientName", m.getRecipientName());
            json.put("recipientEmail", m.getRecipientEmail());
            json.put("subject", m.getSubject());
            json.put("content", m.getContent());
            json.put("timestamp", dateToIso8601(m.getTimestamp()));
            json.put("ownerId", m.getOwnerId());
            json.put("read", m.getRead());
            json.put("starred", m.getStarred());
            JSONArray arr = new JSONArray();
            if (labels != null) for (String l : labels) arr.put(l);
            json.put("labels", arr);
            return json;
        } catch (Exception e) {
            Log.e(TAG, "buildMailJson error: " + e.getMessage(), e);
            return null;
        }
    }

    // -------- Local helper used by InboxViewModel --------
    public List<MailEntity> getMailsForLabelLocal(String labelId) {
        if (labelId == null || labelId.isEmpty()) return new ArrayList<>();
        Log.d(TAG, "getMailsForLabelLocal: " + labelId);
        return mailLabelDao.getMailsForLabelSync(labelId);
    }

    // -------- Writes (blocking; call on background thread) --------
    public void saveMail(MailEntity mail) {
        Log.d(TAG, "saveMail: " + mail.getId());
        mailDao.upsert(mail);
    }

    public void saveMails(List<MailEntity> mails) {
        if (mails == null) mails = new ArrayList<>();
        Log.d(TAG, "saveMails: count=" + mails.size());
        mailDao.upsertAll(mails);
    }

    public int setRead(String mailId, boolean read) {
        Log.d(TAG, "setRead: id=" + mailId + " read=" + read);
        return mailDao.setRead(mailId, read);
    }

    public int setStarred(String mailId, boolean starred) {
        Log.d(TAG, "setStarred: id=" + mailId + " starred=" + starred);
        return mailDao.setStarred(mailId, starred);
    }

    public int deleteMail(String mailId) {
        Log.d(TAG, "deleteMail: id=" + mailId);
        try { mailLabelDao.clearForMail(mailId); } catch (Exception ignore) {}
        return mailDao.deleteById(mailId);
    }

    // -------- Local-only helpers --------
    public void deleteMailLocal(String mailId) {
        try { mailLabelDao.clearForMail(mailId); } catch (Exception ignore) {}
        mailDao.deleteById(mailId);
    }

    public void addLabelToMailLocal(String mailId, String labelId, String ownerIdForLabel, String labelNameFallback) {
        if (labelNameFallback == null || labelNameFallback.isEmpty()) {
            labelNameFallback = labelId;
        }
        labelDao.upsert(new LabelEntity(labelId, ownerIdForLabel, labelNameFallback));
        mailLabelDao.add(new MailLabelCrossRef(mailId, labelId));
    }

    public void removeLabelFromMailLocal(String mailId, String labelId) {
        mailLabelDao.remove(mailId, labelId);
    }

    public void replaceMailLabels(String mailId, List<String> labelIds, String ownerIdForLabels) {
        if (labelIds == null) labelIds = new ArrayList<>();
        List<LabelEntity> toUpsert = new ArrayList<>();
        for (String id : labelIds) {
            toUpsert.add(new LabelEntity(id, ownerIdForLabels, id));
        }
        labelDao.upsertAll(toUpsert);
        mailLabelDao.clearForMail(mailId);
        for (String lid : labelIds) {
            mailLabelDao.add(new MailLabelCrossRef(mailId, lid));
        }
    }

    // == Outbox/Sent helpers for compose flow ==
    public void saveOutboxMailLocal(String id, String ownerId, String to, String subject, String content, Date ts) {
        MailEntity senderMail = new MailEntity(
                id,
                ownerId,
                "Me",
                to != null ? to : "",
                to != null ? to : "",
                to,
                subject,
                content,
                ts,
                ownerId,
                true,
                false
        );
        mailDao.upsert(senderMail);
    }

    public void saveSentMailLocal(String id, String ownerId, String to, String subject, String content, Date ts) {
        MailEntity senderMail = new MailEntity(
                id,
                ownerId,
                "Me",
                to != null ? to : "",
                to != null ? to : "",
                to,
                subject,
                content,
                ts,
                ownerId,
                true,
                false
        );
        mailDao.upsert(senderMail);
    }

    public void ensureLabelAndLink(String mailId, String ownerId, String labelName) {
        labelDao.upsert(new LabelEntity(labelName.toLowerCase(), ownerId, labelName));
        mailLabelDao.add(new MailLabelCrossRef(mailId, labelName.toLowerCase()));
    }

    // ===== Cache remote mails + label cross-refs =====
    public int saveMailsAndLabels(List<MailEntity> mails, Map<String, List<String>> mailIdToLabels) {
        if (mails == null) mails = new ArrayList<>();
        if (mailIdToLabels == null) mailIdToLabels = new java.util.HashMap<>();
        Log.d(TAG, "saveMailsAndLabels: mails=" + mails.size());
        // 1) Save mails
        mailDao.upsertAll(mails);
        // 2) Replace label links per mail
        int crossRefCount = 0;
        for (MailEntity m : mails) {
            String mailId = m.getId();
            List<String> labels = mailIdToLabels.get(mailId);
            if (labels == null) labels = new ArrayList<>();
            // Clear existing refs to avoid duplicates/stale links
            try { mailLabelDao.clearForMail(mailId); } catch (Exception ignore) {}
            for (String raw : labels) {
                if (raw == null || raw.isEmpty()) continue;
                String labelId = normalizeLabelId(raw);
                // Ensure LabelEntity exists (ownerId can be null; use id as name fallback)
                try {
                    labelDao.upsert(new LabelEntity(labelId, m.getOwnerId(), labelId));
                } catch (Exception ignored) {}
                long res = mailLabelDao.add(new MailLabelCrossRef(mailId, labelId));
                if (res != -1) crossRefCount++;
            }
        }
        Log.d(TAG, "saveMailsAndLabels: crossRefs added=" + crossRefCount);
        return crossRefCount;
    }

    private String normalizeLabelId(String id) {
        if (id == null) return null;
        if ("inbox".equalsIgnoreCase(id)) return "primary";
        return id.toLowerCase();
    }

    // NEW: helper used by move operation to decide which labels are "inbox-ish"
    public static boolean isInboxLabel(String label) {
        if (label == null) return false;
        String l = label.toLowerCase();
        return l.equals("primary") || l.equals("inbox") ||
                l.equals("promotions") || l.equals("social") ||
                l.equals("updates") || l.equals("trash") ||
                l.equals("drafts") || l.equals("spam") ||
                l.equals("archive") || l.equals("important");
    }

    // NEW: Clear all inbox-ish labels except "starred" for a mail (local)
    public List<String> clearInboxLabelsForMailLocal(String mailId) {
        List<String> removed = new ArrayList<>();
        try {
            List<String> cur = mailLabelDao.getLabelsForMailSync(mailId);
            if (cur == null) return removed;
            for (String label : cur) {
                if ("starred".equalsIgnoreCase(label)) continue;
                if (isInboxLabel(label)) {
                    mailLabelDao.remove(mailId, normalizeLabelId(label));
                    removed.add(normalizeLabelId(label));
                }
            }
        } catch (Exception ignored) {}
        return removed;
    }

    // NEW: Move a mail locally: clear inbox-ish labels and add target
    public List<String> moveMailLocal(String mailId, String ownerId, String targetLabelRaw) {
        String target = normalizeLabelId(targetLabelRaw);
        if (target == null || target.isEmpty()) return new ArrayList<>();
        // ensure label exists
        try {
            labelDao.upsert(new LabelEntity(target, ownerId, targetLabelRaw));
        } catch (Exception ignored) {}
        // remove old inbox labels and add target
        List<String> removed = clearInboxLabelsForMailLocal(mailId);
        try {
            mailLabelDao.add(new MailLabelCrossRef(mailId, target));
        } catch (Exception ignored) {}
        return removed;
    }

    public List<MailEntity> getMailsForLabelLocal(String labelId, String ownerId) {
        if (labelId == null || labelId.isEmpty() || ownerId == null || ownerId.isEmpty())
            return new java.util.ArrayList<>();
        return mailLabelDao.getMailsForLabelSync(labelId, ownerId);
    }

    // =========================
    // DRAFTS – NEW HELPERS
    // =========================

    /**
     * Create or update a draft locally. Returns the draft id (generates one if null/empty).
     * Use this when the user types anything in Compose and leaves.
     */
    public String upsertDraftLocal(String draftIdOrNull, String ownerId,
                                   String to, String subject, String content, Date ts) {
        String id = (draftIdOrNull == null || draftIdOrNull.isEmpty())
                ? "draft-" + UUID.randomUUID()
                : draftIdOrNull;

        MailEntity existing = mailDao.getByIdSync(id);

        String senderId     = (existing != null) ? existing.getSenderId()      : ownerId;
        String senderName   = (existing != null) ? existing.getSenderName()    : "Me";
        String recipientId  = (existing != null) ? existing.getRecipientId()   : (to != null ? to : "");
        String recipientNm  = (existing != null) ? existing.getRecipientName() : (to != null ? to : "");
        String recipientEm  = (existing != null) ? existing.getRecipientEmail(): to;

        String subj = (subject != null) ? subject : ((existing != null) ? existing.getSubject() : null);
        String body = (content != null) ? content : ((existing != null) ? existing.getContent() : null);
        Date   when = (ts != null) ? ts : ((existing != null) ? existing.getTimestamp() : new Date());

        // NOTE isDraft = true
        MailEntity draft = new MailEntity(
                id,
                senderId,
                senderName,
                (to != null) ? to : recipientId,
                (to != null) ? to : recipientNm,
                (to != null) ? to : recipientEm,
                subj,
                body,
                when,
                ownerId,
                true,
                (existing != null) && existing.getStarred(),
                true // <- mark as draft
        );

        mailDao.upsert(draft);
        ensureLabelAndLink(id, ownerId, LABEL_DRAFTS);
        Log.d(TAG, "upsertDraftLocal: id=" + id);
        return id;
    }


    /** Delete a draft and its label cross-refs. */
    public void deleteDraftLocal(String draftId) {
        if (draftId == null || draftId.isEmpty()) return;
        try { mailLabelDao.clearForMail(draftId); } catch (Exception ignore) {}
        mailDao.deleteById(draftId);
        Log.d(TAG, "deleteDraftLocal: id=" + draftId);
    }

    /**
     * Convert a draft into a sent item (local).
     * Removes "drafts" label and ensures "sent" (or "outbox") as requested.
     * If you want a brand-new id (e.g., server id), pass newId; otherwise we reuse the draft id.
     */
    public String convertDraftLocal(String draftId, String ownerId,
                                    String newIdOrNull,
                                    String to, String subject, String content,
                                    Date ts, boolean markAsSent) {
        if (draftId == null || draftId.isEmpty()) return null;

        String targetId = (newIdOrNull == null || newIdOrNull.isEmpty()) ? draftId : newIdOrNull;

        // Remove old draft row if id changes; otherwise we’ll overwrite the same row.
        if (!targetId.equals(draftId)) {
            deleteDraftLocal(draftId);
        } else {
            try { removeLabelFromMailLocal(targetId, LABEL_DRAFTS); } catch (Exception ignore) {}
        }

        if (markAsSent) {
            saveSentMailLocal(targetId, ownerId, to, subject, content, ts != null ? ts : new Date());
            ensureLabelAndLink(targetId, ownerId, LABEL_SENT);
        } else {
            // sometimes you may want Outbox first, then Sent after server ack
            saveOutboxMailLocal(targetId, ownerId, to, subject, content, ts != null ? ts : new Date());
            ensureLabelAndLink(targetId, ownerId, LABEL_OUTBOX);
        }
        Log.d(TAG, "convertDraftLocal: draftId=" + draftId + " -> targetId=" + targetId +
                " label=" + (markAsSent ? LABEL_SENT : LABEL_OUTBOX));
        return targetId;
    }
}
