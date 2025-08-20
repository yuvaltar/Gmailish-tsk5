// UserMapper.java
package com.example.gmailish.data.mappers;

import com.example.gmailish.data.entity.UserEntity;

import org.json.JSONObject;

public final class UserMapper {

    // Map only the fields supported by your current 4-arg UserEntity constructor.
    // Adjust field order/names below if your constructor differs.
    public static UserEntity fromJson(JSONObject json) {
        String id = json.optString("id", null);
        String username = json.optString("username", null);
        String email = json.optString("email", null);
        String picture = json.optString("picture", null);

        return new UserEntity(
                id,
                username,
                email,
                picture
        );
    }

    private UserMapper() {}
}
