package com.example.gmailish.model;

public class RegisterRequest {
    public String firstName;
    public String lastName;
    public String dob;
    public String gender;
    public String username;
    public String password;

    public RegisterRequest(){

    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public RegisterRequest(String firstName, String lastName, String dob, String gender, String username, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dob = dob;
        this.gender = gender;
        this.username = username;
        this.password = password;
    }
}
