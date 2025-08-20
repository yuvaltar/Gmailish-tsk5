// LabelMapper.java
package com.example.gmailish.data.mappers;


import com.example.gmailish.data.entity.LabelEntity;

import org.json.JSONObject;

public final class LabelMapper {
    public static LabelEntity fromJson(JSONObject json) {
        return new LabelEntity(
                json.optString("id"),
                json.optString("ownerId"),
                json.optString("name", json.optString("id"))
        );
    }
    private LabelMapper() {}
}
