package com.example.gmailish.data.mapper

import com.example.gmailish.data.entity.LabelEntity
import com.example.gmailish.data.entity.MailEntity
import com.example.gmailish.data.entity.MailLabelCrossRef
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object MailMapper {


    private val knownFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    private fun parseDateOrNow(value: String?): Date {
        if (value.isNullOrBlank()) return Date()
        for (pattern in knownFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(value) ?: Date()
            } catch (_: Exception) {}
        }
        return value.toLongOrNull()?.let { Date(it) } ?: Date()
    }

    private fun toLabelIdList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val v = array.optString(i, null)
            if (!v.isNullOrBlank()) out.add(v)
        }
        return out
    }

    fun mailEntityFromJson(json: JSONObject): MailEntity {
        return MailEntity(
            id = json.optString("id"),
            senderId = json.optString("senderId"),
            senderName = json.optString("senderName"),
            recipientId = json.optString("recipientId"),
            recipientName = json.optString("recipientName"),
            recipientEmail = json.optString("recipientEmail"),
            subject = json.optString("subject"),
            content = json.optString("content"),
            timestamp = parseDateOrNow(json.optString("timestamp", null)),
            ownerId = json.optString("ownerId"),
            read = json.optBoolean("read", false),
            starred = json.optBoolean("starred", false)
        )
    }

    fun labelIdsFromJson(json: JSONObject): List<String> {
        val arr = if (json.has("labels")) json.optJSONArray("labels") else null
        return toLabelIdList(arr)
    }

    fun crossRefsForMail(mailId: String, labelIds: List<String>): List<MailLabelCrossRef> {
        return labelIds.map { lid -> MailLabelCrossRef(mailId = mailId, labelId = lid) }
    }

    // Optional helper if you have label objects from another endpoint
    fun labelEntityFromJson(json: JSONObject): LabelEntity {
        val id = json.optString("id")
        val ownerId = json.optString("ownerId")
        val name = json.optString("name", id)
        return LabelEntity(id = id, ownerId = ownerId, name = name)
    }
}