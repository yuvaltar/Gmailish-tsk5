package com.example.gmailish.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "blacklist")
public class BlacklistEntity {
    @PrimaryKey @NonNull public String url;


    public Date createdAt;
    public Date updatedAt;

    public BlacklistEntity(@NonNull String url, Date createdAt, Date updatedAt) {
        this.url = url;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}