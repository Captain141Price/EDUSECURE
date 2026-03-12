package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.CourseOffering;
import com.example.facultyblockchain.model.StudentExcelModel;
import com.example.facultyblockchain.service.CourseOfferingService;
import com.example.facultyblockchain.service.StudentExcelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/course")
@CrossOrigin("*")
public class CourseOfferingController {

    @Autowired
    private CourseOfferingService courseOfferingService;

    @Autowired
    private StudentExcelService studentExcelService;

    @PostMapping("/allocate")
    public ResponseEntity<?> allocateCourse(@RequestBody CourseOffering offering) {
        try {
            courseOfferingService.allocateCourse(offering);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Course allocated successfully."));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Error allocating course."));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<CourseOffering>> getAllOfferings() {
        return ResponseEntity.ok(courseOfferingService.getAllOfferings());
    }

    @GetMapping("/by-teacher")
    public ResponseEntity<List<CourseOffering>> getOfferingsByTeacher(@RequestParam String teacherEmail) {
        return ResponseEntity.ok(courseOfferingService.getOfferingsByTeacher(teacherEmail));
    }

    @GetMapping("/students")
    public ResponseEntity<List<StudentExcelModel>> getStudentsForCourse(
            @RequestParam String department,
            @RequestParam String semester) {
        List<StudentExcelModel> students = studentExcelService.filterStudents(department, null, semester);
        return ResponseEntity.ok(students);
    }

    /**
     * Saves selected students for a specific course allocation.
     */
    @PostMapping("/enrollment/save")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> saveEnrollment(@RequestBody Map<String, Object> payload) {
        try {
            String courseId = (String) payload.get("courseId");
            List<Map<String, String>> studentsData = (List<Map<String, String>>) payload.get("students");
            List<StudentExcelModel> students = new ArrayList<>();
            for (Map<String, String> s : studentsData) {
                students.add(new StudentExcelModel(
                    s.get("name"), s.get("email"), s.get("classRoll"), s.get("roll"),
                    s.get("semester"), "", s.get("department")
                ));
            }
            courseOfferingService.saveEnrollment(courseId, students);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Returns enrolled students for a specific course.
     */
    @GetMapping("/enrollment")
    public ResponseEntity<List<StudentExcelModel>> getEnrolledStudents(@RequestParam String courseId) {
        return ResponseEntity.ok(courseOfferingService.getEnrolledStudents(courseId));
    }
}
