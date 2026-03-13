package com.example.facultyblockchain.model;

import java.io.Serializable;

public class StudentExcelModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String email;
    private String roll;
    private String semester;
    private String passingYear;
    private String department;
    private String classRoll;

    // constructor
    public StudentExcelModel(String name, String email, String roll,
                             String semester, String passingYear, String department) {
        this.name = name;
        this.email = email;
        this.roll = roll;
        this.semester = semester;
        this.passingYear = passingYear;
        this.department = department;
    }

    public StudentExcelModel(String name, String email, String classRoll, String roll,
                             String semester, String passingYear, String department) {
        this.name = name;
        this.email = email;
        this.classRoll = classRoll;
        this.roll = roll;
        this.semester = semester;
        this.passingYear = passingYear;
        this.department = department;
    }

    // getters
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRoll() { return roll; }
    public String getSemester() { return semester; }
    public String getPassingYear() { return passingYear; }
    public String getDepartment() { return department; }
    public String getClassRoll() { return classRoll; }
    public void setClassRoll(String classRoll) { this.classRoll = classRoll; }
}
