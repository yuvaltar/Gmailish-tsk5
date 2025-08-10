package com.example.gmailish.data.mapper

import com.example.gmailish.data.entity.UserEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object UserMapper {


    private val knownFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
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

    fun fromJson(json: JSONObject): UserEntity {
        return UserEntity(
            id = json.optString("id"),
            firstName = json.optString("firstName"),
            lastName = json.optString("lastName"),
            username = json.optString("username"),
            email = json.optString("email"),
            gender = json.optString("gender"),
            birthdate = parseDateOrNow(json.optString("birthdate", null)),
            picture = json.optString("picture", "")
        )
    }
}