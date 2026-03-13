package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.service.BlockchainService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@CrossOrigin("*")
public class StudentDashboardController {

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private com.example.facultyblockchain.service.SubjectCatalogService subjectCatalogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Directory for uploaded certificates
    private static final String UPLOAD_DIR = "uploads/";

    // 1. DASHBOARD SUMMARY
    @GetMapping("/dashboard/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary(@RequestParam String email) {
        try {
            Map<String, Object> summary = new HashMap<>();

            // Calculate overall attendance
            double totalPresent = 0;
            double totalClasses = 0;
            List<Block> attBlocks = blockchainService.getAttendanceBlocks();
            for (Block b : attBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty())
                    continue;
                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(),
                            new TypeReference<Map<String, Object>>() {
                            });
                    List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
                    if (students != null) {
                        for (Map<String, Object> st : students) {
                            // FIX: prefer email match; fall back to name only for legacy blocks without email field
                            String storedEmail = (String) st.get("email");
                            String storedName  = (String) st.get("name");
                            boolean matched = (storedEmail != null && !storedEmail.isEmpty())
                                    ? email.equalsIgnoreCase(storedEmail)
                                    : (storedName != null && email.equalsIgnoreCase(storedName));
                            if (matched) {
                                List<Boolean> attList = (List<Boolean>) st.get("attendance");
                                if (attList != null) {
                                    for (Boolean present : attList) {
                                        if (present != null) {
                                            totalClasses++;
                                            if (present)
                                                totalPresent++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    // ignore parse errors for older blocks
                }
            }
            double attendancePerc = totalClasses == 0 ? 0 : (totalPresent / totalClasses) * 100;
            summary.put("attendancePercentage", Double.parseDouble(new DecimalFormat("##.##").format(attendancePerc)));

            // Calculate MAR score
            int totalMar = 0;
            List<Block> marBlocks = blockchainService.getMarBlocks();

            // First: gather review statuses
            Map<String, String> marReviewStatuses = new HashMap<>();
            for (Block b : marBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                    if ("MENTOR_REVIEW".equals(data.get("action"))) {
                        String originalHash = (String) data.get("originalHash");
                        String status = (String) data.get("status");
                        if (originalHash != null && status != null) {
                            marReviewStatuses.put(originalHash, status);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Second: sum points ONLY for Approved blocks
            for (Block b : marBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                if (!email.equalsIgnoreCase(b.getEmail())) continue;

                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                    if ("MENTOR_REVIEW".equals(data.get("action"))) continue;

                    String blockHash = b.getHash();
                    String status = marReviewStatuses.getOrDefault(blockHash, "Pending");

                    if ("Approved".equalsIgnoreCase(status)) {
                        Object pointsObj = data.get("points");
                        if (pointsObj != null) {
                            totalMar += Integer.parseInt(pointsObj.toString());
                        }
                    }
                } catch (Exception ignored) {}
            }
            summary.put("marScore", totalMar);

            // Calculate MOOCs credits
            int totalMoocs = 0;
            List<Block> moocsBlocks = blockchainService.getMoocsBlocks();
            for (Block b : moocsBlocks) {
                if (email.equalsIgnoreCase(b.getEmail()) && b.getProjects() != null && !b.getProjects().isEmpty()) {
                    try {
                        Map<String, Object> data = objectMapper.readValue(b.getProjects(),
                                new TypeReference<Map<String, Object>>() {
                                });
                        Object creditsObj = data.get("credits");
                        if (creditsObj != null) {
                            totalMoocs += Integer.parseInt(creditsObj.toString());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            summary.put("moocsCredits", totalMoocs);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. VIEW MARKS
    @GetMapping("/marks")
    public ResponseEntity<?> getStudentMarks(@RequestParam String email) {
        try {
            List<Map<String, Object>> studentMarksList = new ArrayList<>();
            List<Block> marksBlocks = blockchainService.getMarksBlocks();

            for (Block b : marksBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty())
                    continue;
                try {
                    Map<String, Object> blockData = objectMapper.readValue(b.getProjects(),
                            new TypeReference<Map<String, Object>>() {
                            });

                    // ── NEW FORMAT: marks.html saves {meta, students:[{name,email,marks:[]}], examLabels} ──
                    List<Map<String, Object>> students = (List<Map<String, Object>>) blockData.get("students");
                    Map<String, Object> meta = (Map<String, Object>) blockData.get("meta");

                    if (students == null) continue;

                    // Subject name comes from meta.subjectName
                    String subjectName = (meta != null && meta.containsKey("subjectName"))
                            ? (String) meta.get("subjectName")
                            : "Unknown Subject";

                    // Semester comes from meta.year (the course semester)
                    String semester = (meta != null && meta.containsKey("year"))
                            ? meta.get("year").toString()
                            : "--";

                    for (Map<String, Object> st : students) {
                        // Match student by email (primary) or name (legacy fallback)
                        String storedEmail = (String) st.get("email");
                        String storedName  = (String) st.get("name");
                        boolean matched = (storedEmail != null && !storedEmail.isEmpty())
                                ? email.equalsIgnoreCase(storedEmail)
                                : (storedName != null && email.equalsIgnoreCase(storedName));
                        if (!matched) continue;

                        // Read marks array: [{name, email, classRoll, univRoll, marks:[n1,n2,...]}]
                        List<Object> marksList = (List<Object>) st.get("marks");
                        double total = 0;
                        List<Double> validMarks = new ArrayList<>();
                        if (marksList != null) {
                            for (Object cellVal : marksList) {
                                if (cellVal != null && !cellVal.toString().trim().isEmpty()) {
                                    try {
                                        double m = Double.parseDouble(cellVal.toString());
                                        total += m;
                                        validMarks.add(m);
                                    } catch (NumberFormatException ignored) {
                                        validMarks.add(null);
                                    }
                                } else {
                                    validMarks.add(null);
                                }
                            }
                        }
                        
                        String examType = (meta != null && meta.containsKey("examType")) ? meta.get("examType").toString() : "CA";

                        Map<String, Object> markEntry = new HashMap<>();
                        markEntry.put("courseCode", subjectName); // The raw course code
                        markEntry.put("semester", semester);
                        markEntry.put("examType", examType);
                        markEntry.put("marksList", validMarks);
                        markEntry.put("total", Double.parseDouble(new DecimalFormat("##.##").format(total)));
                        
                        if ("CA".equalsIgnoreCase(examType) || "THEORY".equalsIgnoreCase(examType)) {
                            markEntry.put("percentage", Double.parseDouble(new DecimalFormat("##.##").format(total))); // out of 100
                        } else {
                            double perc = (total / 80.0) * 100.0;
                            markEntry.put("percentage", Double.parseDouble(new DecimalFormat("##.##").format(perc)));
                        }

                        // Try resolving the actual subject name so frontend display can use it
                        String dept = (meta != null && meta.containsKey("departmentName")) ? meta.get("departmentName").toString() : "";
                        String sem = (meta != null && meta.containsKey("year")) ? meta.get("year").toString() : "";
                        String resolvedName = subjectName;
                        if (!dept.isEmpty() && !sem.isEmpty()) {
                            var entryOpt = subjectCatalogService.findByCourseCode(dept, sem, subjectName);
                            if (entryOpt.isPresent()) {
                                resolvedName = entryOpt.get().getCourseName();
                            }
                        }
                        markEntry.put("subject", resolvedName);
                        markEntry.put("savedAt", meta != null ? meta.get("savedAt") : null);

                        studentMarksList.add(markEntry);
                    }
                } catch (Exception ignored) {
                }
            }
            studentMarksList.sort(Comparator
                    .comparingInt((Map<String, Object> entry) -> parseSemesterValue(entry.get("semester")))
                    .thenComparing(entry -> String.valueOf(entry.getOrDefault("subject", "")))
                    .thenComparing(entry -> String.valueOf(entry.getOrDefault("examType", ""))));
            return ResponseEntity.ok(studentMarksList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 3. VIEW ATTENDANCE
    @GetMapping("/attendance")
    public ResponseEntity<?> getStudentAttendance(@RequestParam String email) {
        try {
            Map<String, Map<String, Object>> latestPerSubject = new LinkedHashMap<>();

            List<Block> attBlocks = blockchainService.getAttendanceBlocks();

            for (Block b : attBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                try {
                    Map<String, Object> blockData = objectMapper.readValue(b.getProjects(),
                            new TypeReference<Map<String, Object>>() {});
                    List<Map<String, Object>> students = (List<Map<String, Object>>) blockData.get("students");
                    Map<String, Object> meta = (Map<String, Object>) blockData.get("meta");

                    if (students == null) continue;

                    String courseCode = (meta != null && meta.containsKey("subjectName"))
                            ? (String) meta.get("subjectName")
                            : "Unknown Subject";
                    String semester = (meta != null && meta.containsKey("year"))
                            ? meta.get("year").toString()
                            : "--";
                    String savedAt = (meta != null && meta.containsKey("savedAt"))
                            ? String.valueOf(meta.get("savedAt"))
                            : "";
                             
                    String dept = (meta != null && meta.containsKey("departmentName")) ? meta.get("departmentName").toString() : "";
                    String sem = (meta != null && meta.containsKey("year")) ? meta.get("year").toString() : "";
                    String subjectName = courseCode;
                    
                    if (!dept.isEmpty() && !sem.isEmpty() && !courseCode.equals("Unknown Subject")) {
                        var entryOpt = subjectCatalogService.findByCourseCode(dept, sem, courseCode);
                        if (entryOpt.isPresent()) {
                            subjectName = entryOpt.get().getCourseName();
                        }
                    }

                    for (Map<String, Object> st : students) {
                        // BUG FIX: match ONLY by email — name fallback caused wrong data
                        String storedEmail = (String) st.get("email");
                        if (storedEmail == null || storedEmail.isEmpty()) continue;
                        if (!email.equalsIgnoreCase(storedEmail)) continue;

                        // Build attendance entry for this subject
                        List<Boolean> attList = (List<Boolean>) st.get("attendance");
                        int totalClasses = 0, present = 0;
                        if (attList != null) {
                            for (Boolean isPresent : attList) {
                                if (isPresent != null) {
                                    totalClasses++;
                                    if (isPresent) present++;
                                }
                            }
                        }
                        double perc = totalClasses == 0 ? 0
                                : ((double) present / totalClasses) * 100;

                        Map<String, Object> attEntry = new HashMap<>();
                        attEntry.put("subject", subjectName);
                        attEntry.put("courseCode", courseCode);
                        attEntry.put("semester", semester);
                        attEntry.put("totalClasses", totalClasses);
                        attEntry.put("present", present);
                        attEntry.put("percentage",
                                Double.parseDouble(new DecimalFormat("##.##").format(perc)));
                        attEntry.put("savedAt", savedAt);

                        String entryKey = semester + "::" + courseCode;
                        latestPerSubject.put(entryKey, attEntry);
                    }
                } catch (Exception ignored) {}
            }

            List<Map<String, Object>> sortedAttendance = latestPerSubject.values().stream()
                    .sorted(Comparator
                            .comparingInt((Map<String, Object> entry) -> parseSemesterValue(entry.get("semester")))
                            .thenComparing(entry -> String.valueOf(entry.getOrDefault("subject", ""))))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(sortedAttendance);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. MAR List
    @GetMapping("/mar/list")
    public ResponseEntity<?> getMarList(@RequestParam String email) {
        try {
            List<Map<String, Object>> marList = new ArrayList<>();
            List<Block> blocks = blockchainService.getMarBlocks();
            
            // First pass: aggregate all reviews
            Map<String, String> reviewStatuses = new HashMap<>(); // originalHash -> Status
            for (Block b : blocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                    if ("MENTOR_REVIEW".equals(data.get("action"))) {
                        String originalHash = (String) data.get("originalHash");
                        String status = (String) data.get("status");
                        if (originalHash != null && status != null) {
                            reviewStatuses.put(originalHash, status);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Second pass: collect student's submissions
            for (Block b : blocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                    
                    // Skip review blocks
                    if ("MENTOR_REVIEW".equals(data.get("action"))) continue;

                    // Ensure email matches student
                    if (b.getEmail() != null && email.equalsIgnoreCase(b.getEmail().trim())) {
                        data.put("hash", b.getHash());
                        marList.add(data);
                    }
                } catch (Exception ignored) {}
            }

            // Third pass: apply statuses synchronously just like MentorController
            for (Map<String, Object> sub : marList) {
                String hash = (String) sub.get("hash");
                sub.put("status", reviewStatuses.getOrDefault(hash, "Pending"));
            }

            return ResponseEntity.ok(marList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 5. Submit MAR
    @PostMapping("/mar/submit")
    public ResponseEntity<?> submitMar(@RequestBody Map<String, Object> payload) {
        try {
            String email = (String) payload.get("email");
            payload.put("submittedAt", new Date().getTime());
            String json = objectMapper.writeValueAsString(payload);

            boolean added = blockchainService.addBlock(
                    "MAR_RECORD", email, "Student",
                    "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", json);

            if (added)
                return ResponseEntity.ok(Map.of("status", "success", "message", "MAR Activity saved to blockchain"));
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to save MAR"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 6. MOOCs List
    @GetMapping("/moocs/list")
    public ResponseEntity<?> getMoocsList(@RequestParam String email) {
        try {
            List<Map<String, Object>> moocsList = new ArrayList<>();
            List<Block> blocks = blockchainService.getMoocsBlocks();
            for (Block b : blocks) {
                if (email.equalsIgnoreCase(b.getEmail()) && b.getProjects() != null && !b.getProjects().isEmpty()) {
                    try {
                        Map<String, Object> data = objectMapper.readValue(b.getProjects(),
                                new TypeReference<Map<String, Object>>() {
                                });
                        data.put("hash", b.getHash());
                        moocsList.add(data);
                    } catch (Exception ignored) {
                    }
                }
            }
            return ResponseEntity.ok(moocsList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 7. Submit MOOCs
    @PostMapping("/moocs/submit")
    public ResponseEntity<?> submitMoocs(@RequestBody Map<String, Object> payload) {
        try {
            String email = (String) payload.get("email");
            payload.put("submittedAt", new Date().getTime());
            String json = objectMapper.writeValueAsString(payload);

            boolean added = blockchainService.addBlock(
                    "MOOCS_RECORD", email, "Student",
                    "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", json);

            if (added)
                return ResponseEntity.ok(Map.of("status", "success", "message", "MOOCs Course saved to blockchain"));
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to save MOOCs"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 8. Blockchain Verification
    @GetMapping("/verify")
    public ResponseEntity<?> verifyPersonalChain(@RequestParam String email) {
        try {
            boolean valid = blockchainService.isChainValid();

            List<Map<String, Object>> personalBlocks = new ArrayList<>();
            List<Block> allBlocks = blockchainService.getAllBlocks();
            for (Block b : allBlocks) {
                if (email.equalsIgnoreCase(b.getEmail())) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("type", b.getName());
                    info.put("hash", b.getHash());
                    info.put("previousHash", b.getPreviousHash());
                    info.put("timestamp", b.getHash()); // using calculate hash implicitly via getter time? No, it's
                                                        // missing timestamp getter. Oh wait, getHash has timestamp in
                                                        // calculation.

                    // Try to extract timestamp if present in data, otherwise just return hash
                    String ts = "Recent";
                    try {
                        if (b.getProjects() != null && !b.getProjects().isEmpty()) {
                            Map<String, Object> data = objectMapper.readValue(b.getProjects(),
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            if (data.containsKey("submittedAt")) {
                                ts = new java.util.Date(Long.parseLong(data.get("submittedAt").toString()))
                                        .toLocaleString();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    info.put("time", ts);
                    personalBlocks.add(info);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "valid", valid,
                    "blocks", personalBlocks));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 9. Upload file endpoint
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String filename = System.currentTimeMillis() + "_"
                    + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9.-]", "_");
            Path path = Paths.get(UPLOAD_DIR + filename);
            Files.write(path, file.getBytes());

            return ResponseEntity.ok(Map.of("status", "success", "filepath", filename));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "message", "Upload failed: " + e.getMessage()));
        }
    }

    private int parseSemesterValue(Object semesterValue) {
        if (semesterValue == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(semesterValue.toString().trim());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
