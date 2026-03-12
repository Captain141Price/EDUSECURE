package com.example.facultyblockchain.model;

import java.util.List;

public class BulkSemesterRequest {

    private List<String> emails;
    private String semester;

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }
}