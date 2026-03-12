package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.CourseOffering;
import com.example.facultyblockchain.service.CourseOfferingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/course")
@CrossOrigin("*")
public class CourseOfferingController {

    @Autowired
    private CourseOfferingService courseOfferingService;

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
}
