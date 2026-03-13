package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.service.ArchiveBlockchainService;
import com.example.facultyblockchain.service.SemesterRolloverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/archive")
public class ArchiveController {

    @Autowired
    private SemesterRolloverService rolloverService;

    @Autowired
    private ArchiveBlockchainService archiveService;

    @PostMapping("/rollover")
    public ResponseEntity<Map<String, Object>> performRollover(
            @RequestBody Map<String, String> request) {

        String passingYear = request.get("passingYear");
        if (passingYear == null || passingYear.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "passingYear is required in request body (e.g. {\"passingYear\": \"2026\"})"));
        }

        try {
            Integer.parseInt(passingYear.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "passingYear must be numeric"));
        }

        SemesterRolloverService.RolloverResult result =
                rolloverService.performRollover(passingYear.trim());

        if (result.success) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "semTag", result.semTag,
                    "message", result.message));
        }

        return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", result.message));
    }

    @GetMapping("/timeline")
    public ResponseEntity<Map<String, Object>> getStudentTimeline(
            @RequestParam String universityRoll) {

        if (universityRoll == null || universityRoll.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "universityRoll parameter is required"));
        }

        Map<String, Map<String, Object>> timeline =
                archiveService.getStudentTimeline(universityRoll.trim());

        if (timeline.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "universityRoll", universityRoll.trim(),
                    "message", "No active or archived records found for this university roll number",
                    "semesters", List.of()));
        }

        Map<String, Object> firstSection = timeline.values().iterator().next();
        List<Map<String, Object>> sections = new ArrayList<>(timeline.values());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", true);
        response.put("studentName", firstSection.getOrDefault("studentName", "Unknown"));
        response.put("universityRoll", universityRoll.trim());
        response.put("department", firstSection.getOrDefault("department", "Unknown"));
        response.put("email", firstSection.getOrDefault("email", ""));
        response.put("passingYear", firstSection.getOrDefault("passingYear", ""));
        response.put("semester", firstSection.getOrDefault("semester", ""));
        response.put("totalSemesters", sections.size());
        response.put("semesters", sections);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/semesters")
    public ResponseEntity<Map<String, Object>> listSemesters() {
        List<String> sessions = archiveService.listArchivedSemesters();
        return ResponseEntity.ok(Map.of(
                "count", sessions.size(),
                "semesters", sessions));
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles() {
        List<String> files = archiveService.listArchiveFiles();
        return ResponseEntity.ok(Map.of(
                "archiveDirectory", System.getProperty("user.dir"),
                "fileCount", files.size(),
                "files", files));
    }

    @GetMapping("/student/find")
    public ResponseEntity<Map<String, Object>> findStudent(
            @RequestParam String universityRoll) {

        Map<String, String> identity = archiveService.findStudentIdentity(universityRoll.trim());

        if (identity == null) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "No student found with university roll: " + universityRoll));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("found", true);
        response.put("name", identity.get("name"));
        response.put("email", identity.get("email"));
        response.put("department", identity.get("department"));
        response.put("universityRoll", identity.get("universityRoll"));
        response.put("passingYear", identity.get("passingYear"));
        response.put("semester", identity.get("semester"));
        response.put("source", identity.get("source"));
        return ResponseEntity.ok(response);
    }
}
