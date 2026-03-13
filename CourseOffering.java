package com.example.facultyblockchain.model;

public class CourseOffering {
    private String courseId;       // = courseCode from subjects.xlsx (e.g. PEC-CS801B)
    private String courseName;     // Subject display name
    private String subjectType;    // THEORY / PRACTICAL / SESSIONAL
    private String teacherEmail;   // Reference to the teacher
    private String department;
    private String semester;
    private String courseCode;     // Explicit course code — same value as courseId,
                                   // kept as a separate field for clarity in the UI

    public CourseOffering() {}

    /**
     * Legacy 6-arg constructor — keeps backward compatibility with existing
     * dat-file rows that do NOT have a courseCode column.
     * courseCode defaults to courseId in this case.
     */
    public CourseOffering(String courseId, String courseName, String subjectType,
            String teacherEmail, String department, String semester) {
        this.courseId    = courseId;
        this.courseName  = courseName;
        this.subjectType = subjectType;
        this.teacherEmail= teacherEmail;
        this.department  = department;
        this.semester    = semester;
        this.courseCode  = courseId; // backward-compat default
    }

    /**
     * Full 7-arg constructor used for new allocations that carry an explicit
     * courseCode from subjects.xlsx.
     */
    public CourseOffering(String courseId, String courseName, String subjectType,
            String teacherEmail, String department, String semester, String courseCode) {
        this.courseId    = courseId;
        this.courseName  = courseName;
        this.subjectType = subjectType;
        this.teacherEmail= teacherEmail;
        this.department  = department;
        this.semester    = semester;
        this.courseCode  = (courseCode != null && !courseCode.isBlank())
                           ? courseCode : courseId;
    }

    // Getters and Setters
    public String getCourseId()   { return courseId; }
    public void   setCourseId(String courseId) {
        this.courseId = courseId;
        // Keep courseCode in sync if it was not set independently
        if (this.courseCode == null || this.courseCode.isBlank()) {
            this.courseCode = courseId;
        }
    }

    public String getCourseName()  { return courseName; }
    public void   setCourseName(String courseName) { this.courseName = courseName; }

    public String getSubjectType() { return subjectType; }
    public void   setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getTeacherEmail() { return teacherEmail; }
    public void   setTeacherEmail(String teacherEmail) { this.teacherEmail = teacherEmail; }

    public String getDepartment() { return department; }
    public void   setDepartment(String department) { this.department = department; }

    public String getSemester()   { return semester; }
    public void   setSemester(String semester) { this.semester = semester; }

    public String getCourseCode() {
        return (courseCode != null && !courseCode.isBlank()) ? courseCode : courseId;
    }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
}
