package com.example.facultyblockchain.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

public class LoginBlock implements Serializable {
    private static final long serialVersionUID = 1L;

    private int index;
    private String timestamp;
    private String name;
    private String email;
    private String hashedPassword;
    private String previousHash;
    private String hash;

    public LoginBlock(int index, String name, String email, String hashedPassword, String previousHash) {
        this.index = index;
        this.timestamp = new Date().toString();
        this.name = name;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.previousHash = previousHash;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        try {
            String data = index + timestamp + name + email + hashedPassword + previousHash;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error calculating hash", e);
        }
    }

    // Getters
    public int getIndex() {
        return index;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }

    // Setters
    public void setIndex(int index) {
        this.index = index;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}