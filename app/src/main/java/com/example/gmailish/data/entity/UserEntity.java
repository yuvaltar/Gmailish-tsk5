package com.example.gmailish.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {

    @PrimaryKey @NonNull
    private String id;

    private String username;
    private String picture;
    private String pictureUrl;

    public UserEntity(@NonNull String id, String username, String picture, String pictureUrl) {
        this.id = id;
        this.username = username;
        this.picture = picture;
        this.pictureUrl = pictureUrl;
    }

    @NonNull public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPicture() { return picture; }
    public String getPictureUrl() { return pictureUrl; }

    public void setId(@NonNull String id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPicture(String picture) { this.picture = picture; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }
}
