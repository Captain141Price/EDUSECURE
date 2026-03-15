package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.TeacherMasterRecord;
import com.example.facultyblockchain.service.TeacherMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/faculty-master")
@CrossOrigin("*")
public class TeacherMasterController {

    @Autowired
    private TeacherMasterService teacherMasterService;

    @GetMapping("/all")
    public ResponseEntity<List<TeacherMasterRecord>> getAllTeachers() {
        return ResponseEntity.ok(teacherMasterService.getAllTeachers());
    }

    @GetMapping("/by-email")
    public ResponseEntity<?> getByEmail(@RequestParam String email) {
        return teacherMasterService.findByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("status", "error", "message", "Faculty not found")));
    }

    @PostMapping("/upload-excel")
    public ResponseEntity<Map<String, Object>> uploadTeachersExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Excel file is empty"));
        }

        try {
            int count = teacherMasterService.importTeachersExcel(file);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", count + " teacher records imported into teachers_master.dat. New teacher login accounts use default password teacher@123."
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed to import teachers.xlsx: " + e.getMessage()));
        }
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportFacultyProfiles() throws IOException {
        File exportFile = teacherMasterService.exportFacultyProfiles();
        FileSystemResource resource = new FileSystemResource(exportFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + exportFile.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(exportFile.length())
                .body(resource);
    }
}
