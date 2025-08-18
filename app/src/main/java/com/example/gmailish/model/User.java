package com.example.gmailish.model;

public class User {
    private String id;
    private String username;
    private String picture;
    private String pictureUrl;

    public User(String id, String username, String picture, String pictureUrl) {
        this.id = id;
        this.username = username;
        this.picture = picture;
        this.pictureUrl = pictureUrl;
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

    public String getPictureUrl() { return pictureUrl; }
}
