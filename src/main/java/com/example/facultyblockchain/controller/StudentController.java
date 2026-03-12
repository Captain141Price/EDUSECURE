package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.service.LoginBlockchainService;
import com.example.facultyblockchain.service.StudentExcelService;
import com.example.facultyblockchain.model.StudentExcelModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/student")
@CrossOrigin("*")
public class StudentController {

        /** Whitelist of valid department codes. Any value outside this set is rejected. */
        private static final Set<String> ALLOWED_DEPARTMENTS = Set.of(
                "CSE1", "CSE2", "CSE3", "AIML", "IT", "ECE", "EE"
        );

        private static boolean isValidDepartment(String dept) {
                return dept != null && ALLOWED_DEPARTMENTS.contains(dept.trim().toUpperCase());
        }

        @Autowired
        private StudentExcelService studentExcelService;

        @Autowired
        private LoginBlockchainService loginBlockchainService;

        @PostMapping("/add")
        public Map<String, Object> addStudent(@RequestBody Map<String, String> data) {

                // --- backend department validation ---
                String dept = data.get("department");
                if (!isValidDepartment(dept)) {
                        return Map.of(
                                "status", "error",
                                "message", "Invalid department '" + dept + "'. Allowed values: CSE1, CSE2, CSE3, AIML, IT, ECE, EE");
                }

                if (studentExcelService.emailExists(data.get("email"))) {
                        return Map.of(
                                        "status", "error",
                                        "message", "Student with this email already exists");
                }

                String name       = data.get("name");
                String email      = data.get("email");
                String password   = StudentExcelService.DEFAULT_STUDENT_PASSWORD;
                String roll       = data.get("roll");
                String semester   = data.get("semester");
                String passingYear= data.get("passingYear");
                String department = data.get("department");
                String classRoll  = data.get("classRoll");

                // Validate classRoll — must be present and numeric
                if (classRoll == null || classRoll.isBlank()) {
                        return Map.of("status", "error", "message", "Class Roll is required");
                }
                if (!classRoll.trim().matches("\\d+")) {
                        return Map.of("status", "error", "message", "Class Roll must be a numeric value");
                }

                // Save profile to Excel (with explicit classRoll)
                studentExcelService.saveStudentWithClassRoll(
                                classRoll.trim(), name, email, roll, semester, passingYear, department);

                // Save login to STUDENT blockchain
                loginBlockchainService.addUser(
                                name, email, password, "student");

                return Map.of(
                                "status", "success",
                                "message", "Student saved successfully");
        }

        @PostMapping("/upload-excel")
        public ResponseEntity<Map<String, Object>> uploadExcelFile(
                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
                if (file.isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "File is empty"));
                }
                try {
                        int count = studentExcelService.processExcelUpload(file);
                        return ResponseEntity.ok(Map.of("status", "success", "message",
                                        count + " students uploaded and saved successfully"));
                } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of("status", "error", "message",
                                        "Error processing file: " + e.getMessage()));
                }
        }

        @GetMapping("/all")
        public List<StudentExcelModel> getAllStudents() {
                return studentExcelService.getAllStudents();
        }

        @GetMapping("/by-email")
        public ResponseEntity<?> getStudent(@RequestParam String email) {

                Optional<StudentExcelModel> student = studentExcelService.findByEmail(email);

                if (student.isPresent()) {
                        return ResponseEntity.ok(student.get()); // 200 → EXISTS
                } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 404 → NOT EXISTS
                }
        }

        @PostMapping("/change-password")
        public ResponseEntity<Map<String, Object>> changePassword(
                        @RequestBody Map<String, String> data) {

                // OTP already verified → trust request
                loginBlockchainService.addUser(
                                data.get("name"),
                                data.get("email"),
                                data.get("newPassword"),
                                "student");

                return ResponseEntity.ok(
                                Map.of(
                                                "status", "success",
                                                "message", "Password updated successfully"));
        }

        @PostMapping("/promote-all")
        public ResponseEntity<Map<String, Object>> promoteAllStudents() {

                studentExcelService.promoteAllStudents();

                return ResponseEntity.ok(
                                Map.of(
                                                "status", "success",
                                                "message",
                                                "All students promoted successfully. Students past semester 8 moved to Alumni."));
        }

        @PostMapping("/update")
        public ResponseEntity<Map<String, Object>> updateStudent(
                        @RequestBody Map<String, String> data) {

                // --- backend department validation ---
                String dept = data.get("department");
                if (!isValidDepartment(dept)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "error",
                                "message", "Invalid department '" + dept + "'. Allowed values: CSE1, CSE2, CSE3, AIML, IT, ECE, EE"));
                }

                // Excel update (always if something changed)
                studentExcelService.updateStudentByEmail(
                                data.get("email"),
                                null,
                                data.get("classRoll"),
                                data.get("roll"),
                                data.get("semester"),
                                data.get("passingYear"),
                                data.get("department"));

                // // Blockchain update ONLY if core fields changed
                // if ("true".equals(data.get("blockchainChanged"))) {
                // loginBlockchainService.addUser(
                // data.get("name"),
                // data.get("email"),
                // data.get("password"),
                // "student"
                // );
                // }

                return ResponseEntity.ok(
                                Map.of(
                                                "status", "success",
                                                "message", "Student updated successfully"));
        }

        @GetMapping("/filter-options")
        public ResponseEntity<Map<String, List<String>>> getFilterOptions() {
                return ResponseEntity.ok(studentExcelService.getFilterOptions());
        }

        @GetMapping("/filter")
        public ResponseEntity<List<StudentExcelModel>> filterStudents(
                        @RequestParam(required = false) String department,
                        @RequestParam(required = false) String passingYear,
                        @RequestParam(required = false) String semester) {

                return ResponseEntity.ok(studentExcelService.filterStudents(department, passingYear, semester));
        }
}