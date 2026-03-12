package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.Block; // <-- NEW: Imported your Block model!
import com.example.facultyblockchain.service.BlockchainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private BlockchainService blockchainService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Save FULL attendance grid as JSON string linked to a Teacher
     */
    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody Map<String, Object> payload) {
        try {
            // 1. Extract the teacher's email from the payload (default if not found)
            String teacherEmail = "attendance@system";
            if (payload.containsKey("email")) {
                teacherEmail = (String) payload.get("email");
            }

            // 2. Convert entire attendance payload to JSON string
            String attendanceJson = objectMapper.writeValueAsString(payload);

            // 3. Store inside ONE field (projects), using the TEACHER'S email
            boolean added = blockchainService.addBlock(
                    "ATTENDANCE_RECORD",     // name (identifier)
                    teacherEmail,            // <-- NOW SAVED UNDER THE TEACHER'S EMAIL
                    "Attendance",            // department
                    "", "", "", "", "", "", "",
                    "", "", "", "", "", "",
                    "", "", "", "", "",
                    "", "", 
                    attendanceJson           // ← FULL GRID STORED HERE
            );

            if (added) {
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Attendance block added successfully for " + teacherEmail
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                        "status", "error",
                        "message", "Block creation failed"
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get ONLY attendance blocks (Optionally filtered by teacher email)
     */
    @GetMapping("/chain")
    public ResponseEntity<?> getAttendanceBlocks(@RequestParam(required = false) String email) {
        try {
            // Because we imported Block, we can use it directly! No more messy Reflection.
            List<Block> allBlocks = blockchainService.getAllBlocks();
            List<Map<String, Object>> attendanceBlocks = new ArrayList<>();

            for (Block block : allBlocks) {
                // Check if it's an attendance block
                if ("ATTENDANCE_RECORD".equals(block.getName())) {

                    // If a specific teacher's email was requested, skip blocks that don't match
                    if (email != null && !email.isEmpty() && !email.equalsIgnoreCase(block.getEmail())) {
                        continue;
                    }

                    // Parse the JSON grid back out of the projects field
                    String json = block.getProjects();
                    Object parsed = objectMapper.readValue(json, Object.class);

                    // Build the clean response
                    Map<String, Object> responseBlock = new HashMap<>();
                    responseBlock.put("hash", block.getHash());
                    responseBlock.put("previousHash", block.getPreviousHash());
                    responseBlock.put("email", block.getEmail()); // Send email to frontend
                    responseBlock.put("attendance", parsed);

                    attendanceBlocks.add(responseBlock);
                }
            }

            return ResponseEntity.ok(attendanceBlocks);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Validate blockchain integrity
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateChain() {
        boolean valid = blockchainService.isChainValid();
        return ResponseEntity.ok(Map.of("valid", valid));
    }
}