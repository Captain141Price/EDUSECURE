package com.example.facultyblockchain.model;

public class CourseOffering {
    private String courseId;
    private String courseName;
    private String subjectType; // "THEORY" or "LAB"
    private String teacherEmail; // Reference to the teacher
    private String department;
    private String semester;

    public CourseOffering() {
    }

    public CourseOffering(String courseId, String courseName, String subjectType,
            String teacherEmail, String department, String semester) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.subjectType = subjectType;
        this.teacherEmail = teacherEmail;
        this.department = department;
        this.semester = semester;
    }

    // Getters and Setters
    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getTeacherEmail() {
        return teacherEmail;
    }

    public void setTeacherEmail(String teacherEmail) {
        this.teacherEmail = teacherEmail;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }
}
