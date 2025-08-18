package com.example.gmailish.data.mapper

import com.example.gmailish.data.entity.LabelEntity
import org.json.JSONObject

object LabelMapper {
    fun fromJson(json: JSONObject): LabelEntity {
        return LabelEntity(
            id = json.optString("id"),
            ownerId = json.optString("ownerId"),
            name = json.optString("name", json.optString("id"))
        )
    }
}