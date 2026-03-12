package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.CourseOffering;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseOfferingService {

    private static final String FILE_PATH = System.getProperty("user.dir") + "/CourseOffering.dat";

    // Format: CourseID|CourseName|SubjectType|TeacherEmail|Department|Semester
    public synchronized void allocateCourse(CourseOffering offering) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(String.format("%s|%s|%s|%s|%s|%s\n",
                    offering.getCourseId(),
                    offering.getCourseName(),
                    offering.getSubjectType(),
                    offering.getTeacherEmail(),
                    offering.getDepartment(),
                    offering.getSemester()));
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
                    offerings.add(new CourseOffering(
                            parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
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
}
