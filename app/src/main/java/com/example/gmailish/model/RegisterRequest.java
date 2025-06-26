package com.example.gmailish.model;

import java.util.Date;

public class RegisterRequest {
    private String firstName;
    private String lastName;
    private Date dob;
    private String gender;
    private String username;
    private String password;


    public RegisterRequest() {
    }

    public RegisterRequest(String firstName, String lastName, Date dob, String gender, String username, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dob = dob;
        this.gender = gender;
        this.username = username;
        this.password = password;
    }

    // Getters
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Date getDob() { return dob; }
    public String getGender() { return gender; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    // Setters
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setDob(Date dob) { this.dob = dob; }
    public void setGender(String gender) { this.gender = gender; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
}
