package com.example.facultyblockchain.model;

/**
 * Represents a single subject row loaded from subjects.xlsx.
 * Columns: Department | Semester | CourseName | CourseCode | SubjectType
 */
public class SubjectEntry {

    private String department;
    private int semester;
    private String courseName;
    private String courseCode;
    private String subjectType;

    public SubjectEntry() {}

    public SubjectEntry(String department, int semester, String courseName,
                        String courseCode, String subjectType) {
        this.department  = department;
        this.semester    = semester;
        this.courseName  = courseName;
        this.courseCode  = courseCode;
        this.subjectType = subjectType;
    }

    // Getters
    public String getDepartment()  { return department; }
    public int    getSemester()    { return semester; }
    public String getCourseName()  { return courseName; }
    public String getCourseCode()  { return courseCode; }
    public String getSubjectType() { return subjectType; }

    // Setters (needed for JSON deserialization if ever used)
    public void setDepartment(String department)   { this.department  = department; }
    public void setSemester(int semester)           { this.semester    = semester; }
    public void setCourseName(String courseName)    { this.courseName  = courseName; }
    public void setCourseCode(String courseCode)    { this.courseCode  = courseCode; }
    public void setSubjectType(String subjectType)  { this.subjectType = subjectType; }
}
