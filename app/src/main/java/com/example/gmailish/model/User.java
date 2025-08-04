package com.example.gmailish.model;

public class User {
    private String id;
    private String username;
    private String picture;

    public User(String id, String username, String picture) {
        this.id = id;
        this.username = username;
        this.picture = picture;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPicture() {
        return picture;
    }
}
