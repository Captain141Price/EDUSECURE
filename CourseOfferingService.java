package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.CourseOffering;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import com.example.facultyblockchain.model.StudentExcelModel;

@Service
public class CourseOfferingService {

    private static final String FILE_PATH = System.getProperty("user.dir") + "/CourseOffering.dat";
    private static final String ENROLLMENT_FILE_PATH = System.getProperty("user.dir") + "/CourseEnrollment.dat";

    // Format: CourseID|CourseName|SubjectType|TeacherEmail|Department|Semester|CourseCode
    public synchronized void allocateCourse(CourseOffering offering) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(String.format("%s|%s|%s|%s|%s|%s|%s\n",
                    offering.getCourseId(),
                    offering.getCourseName(),
                    offering.getSubjectType(),
                    offering.getTeacherEmail(),
                    offering.getDepartment(),
                    offering.getSemester(),
                    offering.getCourseCode()));
        }
    }

    public List<CourseOffering> getAllOfferings() {
        List<CourseOffering> offerings = new ArrayList<>();
        File file = new File(FILE_PATH);
        if (!file.exists())
            return offerings;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 6) {
                    // parts[6] = courseCode (new field); fall back to courseId for old records
                    String courseCode = (parts.length >= 7) ? parts[6] : parts[0];
                    offerings.add(new CourseOffering(
                            parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
                            courseCode));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return offerings;
    }

    public List<CourseOffering> getOfferingsByTeacher(String teacherEmail) {
        List<CourseOffering> result = new ArrayList<>();
        for (CourseOffering offering : getAllOfferings()) {
            if (offering.getTeacherEmail().equalsIgnoreCase(teacherEmail)) {
                result.add(offering);
            }
        }
        return result;
    }

    public synchronized void saveEnrollment(String courseId, List<StudentExcelModel> students) throws IOException {
        // We will append or overwrite? Since admin selects and saves, and we might not support re-editing right away, appending is easiest.
        // Actually, to support re-enrollment, we should rewrite all enrollments, but that's complex to filter out old ones without reading all.
        // Let's just append for now, or read all, remove old for courseId, and write all.
        File file = new File(ENROLLMENT_FILE_PATH);
        List<String> allLines = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith(courseId + "|")) {
                        allLines.add(line);
                    }
                }
            }
        }
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ENROLLMENT_FILE_PATH, false))) {
            for (String line : allLines) {
                bw.write(line + "\n");
            }
            for (StudentExcelModel s : students) {
                // CourseID|Name|Email|ClassRoll|UnivRoll|Department|Semester
                bw.write(String.format("%s|%s|%s|%s|%s|%s|%s\n",
                        courseId,
                        s.getName() != null ? s.getName() : "",
                        s.getEmail() != null ? s.getEmail() : "",
                        s.getClassRoll() != null ? s.getClassRoll() : "",
                        s.getRoll() != null ? s.getRoll() : "",
                        s.getDepartment() != null ? s.getDepartment() : "",
                        s.getSemester() != null ? s.getSemester() : ""));
            }
        }
    }

    public List<StudentExcelModel> getEnrolledStudents(String courseId) {
        List<StudentExcelModel> students = new ArrayList<>();
        File file = new File(ENROLLMENT_FILE_PATH);
        if (!file.exists()) return students;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 7 && parts[0].equals(courseId)) {
                    StudentExcelModel s = new StudentExcelModel(
                        parts[1], // name
                        parts[2], // email
                        parts[3], // classRoll
                        parts[4], // roll
                        parts[6], // semester
                        "",       // passingYear (not used in enrollment file)
                        parts[5]  // department
                    );
                    students.add(s);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return students;
    }
}
