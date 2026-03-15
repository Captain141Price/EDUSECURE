package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.model.StudentExcelModel;
import com.example.facultyblockchain.service.BlockchainService;
import com.example.facultyblockchain.service.MentorAssignmentService;
import com.example.facultyblockchain.service.StudentExcelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/mentor")
@CrossOrigin("*")
public class MentorController {

    @Autowired
    private MentorAssignmentService mentorAssignmentService;

    @Autowired
    private StudentExcelService studentExcelService;

    @Autowired
    private BlockchainService blockchainService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> buildAssignedStudentScope(String teacherEmail) {
        Map<String, String> assignment = mentorAssignmentService.getMentorAssignment(teacherEmail);
        if (assignment == null) {
            return null;
        }

        String targetDept = assignment.get("department");
        String targetPassingYear = assignment.get("passingYear");
        int startRoll = Integer.parseInt(assignment.getOrDefault("startRoll", "0"));
        int endRoll = Integer.parseInt(assignment.getOrDefault("endRoll", "9999"));

        List<StudentExcelModel> assignedStudents = studentExcelService.filterStudents(targetDept, targetPassingYear, null);
        Set<String> assignedEmails = new HashSet<>();
        for (StudentExcelModel s : assignedStudents) {
            if (s.getEmail() == null) {
                continue;
            }
            try {
                String rollStr = (s.getClassRoll() != null && !s.getClassRoll().isEmpty()) ? s.getClassRoll() : s.getRoll();
                int rollInt = Integer.parseInt(rollStr.trim());
                if (rollInt >= startRoll && rollInt <= endRoll) {
                    assignedEmails.add(s.getEmail().toLowerCase());
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Map<String, Object> scope = new HashMap<>();
        scope.put("assignment", assignment);
        scope.put("students", assignedStudents);
        scope.put("emails", assignedEmails);
        return scope;
    }

    // 1. Admin assigns a mentor
    @PostMapping("/assign")
    public ResponseEntity<?> assignMentor(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("teacherEmail");
            String department = payload.get("department");
            String passingYear = payload.get("passingYear");
            String startRollStr = payload.get("startRoll");
            String endRollStr = payload.get("endRoll");

            if (email == null || department == null || passingYear == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing fields"));
            }

            int startRoll = 0;
            int endRoll = 9999;
            try {
                if(startRollStr != null) startRoll = Integer.parseInt(startRollStr.trim());
                if(endRollStr != null) endRoll = Integer.parseInt(endRollStr.trim());
            } catch(NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid roll numbers"));
            }

            mentorAssignmentService.assignMentor(email, department, passingYear, startRoll, endRoll);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Mentor assigned successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 2. Fetch mentor assignment (to toggle UI features)
    @GetMapping("/assignment")
    public ResponseEntity<?> getAssignment(@RequestParam String teacherEmail) {
        Map<String, String> assignment = mentorAssignmentService.getMentorAssignment(teacherEmail);
        if (assignment == null) {
            return ResponseEntity.ok(Map.of("isMentor", false));
        }
        assignment.put("isMentor", "true");
        return ResponseEntity.ok(assignment);
    }

    // 2.5 Admin fetches all mentor assignments
    @GetMapping("/all")
    public ResponseEntity<?> getAllAssignments() {
        return ResponseEntity.ok(mentorAssignmentService.getAllAssignments());
    }

    @DeleteMapping("/assign")
    public ResponseEntity<?> removeAssignment(@RequestParam String teacherEmail) {
        boolean removed = mentorAssignmentService.removeMentor(teacherEmail);
        if (!removed) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "error", "message", "No mentor assignment found for this faculty"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "message", "Mentor assignment removed successfully"));
    }

    // 3. Fetch MAR submissions for Mentor's assigned students
    @GetMapping("/mar/submissions")
    public ResponseEntity<?> getMarSubmissions(@RequestParam String teacherEmail) {
        try {
            Map<String, Object> scope = buildAssignedStudentScope(teacherEmail);
            if (scope == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<StudentExcelModel> assignedStudents = (List<StudentExcelModel>) scope.get("students");
            Set<String> assignedEmails = (Set<String>) scope.get("emails");

            List<Block> marBlocks = blockchainService.getMarBlocks();
            List<Map<String, Object>> submissions = new ArrayList<>();

            // Aggregate statuses based on REVIEW blocks
            Map<String, String> reviewStatuses = new HashMap<>(); // originalHash -> Status

            for (Block b : marBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                    
                    // Is this a REVIEW block by a mentor?
                    if ("MENTOR_REVIEW".equals(data.get("action"))) {
                        String targetHash = (String) data.get("originalHash");
                        String status = (String) data.get("status");
                        if (targetHash != null && status != null) {
                            reviewStatuses.put(targetHash, status);
                        }
                        continue;
                    }

                    // Otherwise, it's a student submission
                    String studentEmail = b.getEmail() != null ? b.getEmail().toLowerCase() : "";
                    if (assignedEmails.contains(studentEmail)) {
                        data.put("hash", b.getHash());
                        data.put("studentEmail", b.getEmail());
                        data.put("studentName", data.getOrDefault("studentName", b.getEmail()));
                        
                        // We also need student Roll for the UI. Looking it up.
                        String roll = "--";
                        for(StudentExcelModel s : assignedStudents){
                            if(studentEmail.equalsIgnoreCase(s.getEmail())){
                                roll = (s.getClassRoll() != null && !s.getClassRoll().isEmpty()) ? s.getClassRoll() : s.getRoll();
                                break;
                            }
                        }
                        data.put("roll", roll);
                        
                        submissions.add(data);
                    }
                } catch (Exception ignored) {}
            }

            // Apply statuses
            for (Map<String, Object> sub : submissions) {
                String hash = (String) sub.get("hash");
                sub.put("status", reviewStatuses.getOrDefault(hash, "Pending"));
            }

            return ResponseEntity.ok(submissions);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 4. Fetch MOOCs submissions (Read Only)
    @GetMapping("/moocs/submissions")
    public ResponseEntity<?> getMoocsSubmissions(@RequestParam String teacherEmail) {
        try {
            Map<String, Object> scope = buildAssignedStudentScope(teacherEmail);
            if (scope == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<StudentExcelModel> assignedStudents = (List<StudentExcelModel>) scope.get("students");
            Set<String> assignedEmails = (Set<String>) scope.get("emails");

            List<Block> moocsBlocks = blockchainService.getMoocsBlocks();
            List<Map<String, Object>> submissions = new ArrayList<>();
            Map<String, String> reviewStatuses = new HashMap<>();

            for (Block b : moocsBlocks) {
                if (b.getProjects() == null || b.getProjects().isEmpty()) continue;
                try {
                    Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});

                    if ("MENTOR_REVIEW".equals(data.get("action"))) {
                        String targetHash = (String) data.get("originalHash");
                        String status = (String) data.get("status");
                        if (targetHash != null && status != null) {
                            reviewStatuses.put(targetHash, status);
                        }
                        continue;
                    }

                    String studentEmail = b.getEmail() != null ? b.getEmail().toLowerCase() : "";
                    if (assignedEmails.contains(studentEmail)) {
                        data.put("hash", b.getHash());
                        data.put("studentEmail", b.getEmail());
                        data.put("studentName", data.getOrDefault("studentName", b.getEmail()));
                        
                        String roll = "--";
                        for(StudentExcelModel s : assignedStudents){
                            if(studentEmail.equalsIgnoreCase(s.getEmail())){
                                roll = (s.getClassRoll() != null && !s.getClassRoll().isEmpty()) ? s.getClassRoll() : s.getRoll();
                                break;
                            }
                        }
                        data.put("roll", roll);

                        submissions.add(data);
                    }
                } catch (Exception ignored) {}
            }

            for (Map<String, Object> sub : submissions) {
                String hash = (String) sub.get("hash");
                sub.put("status", reviewStatuses.getOrDefault(hash, "PENDING"));
            }
            return ResponseEntity.ok(submissions);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/moocs/review")
    public ResponseEntity<?> reviewMoocsSubmission(@RequestBody Map<String, Object> payload) {
        try {
            String mentorEmail = (String) payload.get("mentorEmail");
            String originalHash = (String) payload.get("originalHash");
            String status = (String) payload.get("status");

            if (mentorEmail == null || originalHash == null || status == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing fields"));
            }

            Map<String, Object> scope = buildAssignedStudentScope(mentorEmail);
            if (scope == null) {
                return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Mentor assignment not found"));
            }

            Set<String> assignedEmails = (Set<String>) scope.get("emails");
            boolean canReview = false;
            for (Block block : blockchainService.getMoocsBlocks()) {
                if (!originalHash.equals(block.getHash())) {
                    continue;
                }
                if (block.getEmail() != null && assignedEmails.contains(block.getEmail().toLowerCase())) {
                    canReview = true;
                }
                break;
            }

            if (!canReview) {
                return ResponseEntity.status(403).body(Map.of("status", "error", "message", "You can review only assigned student submissions"));
            }

            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("action", "MENTOR_REVIEW");
            reviewData.put("mentorEmail", mentorEmail);
            reviewData.put("originalHash", originalHash);
            reviewData.put("status", status);
            reviewData.put("reviewedAt", new Date().getTime());

            String json = objectMapper.writeValueAsString(reviewData);

            boolean added = blockchainService.addBlock(
                    "MOOCS_RECORD", mentorEmail, "MentorReview",
                    "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", json);

            if (added) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Review saved to blockchain"));
            }

            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to save review"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 5. Mentor reviews a MAR submission
    @PostMapping("/mar/review")
    public ResponseEntity<?> reviewMarSubmission(@RequestBody Map<String, Object> payload) {
        try {
            String mentorEmail = (String) payload.get("mentorEmail");
            String originalHash = (String) payload.get("originalHash");
            String status = (String) payload.get("status"); // "Approved" or "Declined"

            if (mentorEmail == null || originalHash == null || status == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing fields"));
            }

            // Create a REVIEW block
            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("action", "MENTOR_REVIEW");
            reviewData.put("mentorEmail", mentorEmail);
            reviewData.put("originalHash", originalHash);
            reviewData.put("status", status);
            reviewData.put("reviewedAt", new Date().getTime());

            String json = objectMapper.writeValueAsString(reviewData);

            boolean added = blockchainService.addBlock(
                    "MAR_RECORD", mentorEmail, "MentorReview",
                    "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", json);

            if (added) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Review saved to blockchain"));
            }

            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to save review"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
