package com.example.gmailish.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "labels",
        indices = {
                @Index(value = {"ownerId"}),
                @Index(value = {"ownerId", "name"}, unique = true)
        }
)
public class LabelEntity {

    @PrimaryKey @NonNull public String id;
    public String ownerId;
    public String name;

    public LabelEntity(@NonNull String id, String ownerId, String name) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
    }
}
