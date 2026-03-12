package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.service.BlockchainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/marks")
public class MarksController {

    @Autowired
    private BlockchainService blockchainService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/latest")
    public ResponseEntity<?> getLatestState(@RequestParam(required = false) String email) {
        try {
            List<Block> chain = blockchainService.getMarksBlocks();
            Block latestBlock = null;

            for (int i = chain.size() - 1; i >= 0; i--) {
                Block b = chain.get(i);
                if (email == null || email.equalsIgnoreCase(b.getEmail())) {
                    latestBlock = b;
                    break;
                }
            }

            // Crash-Proof check: Make sure the block actually has data
            if (latestBlock == null || latestBlock.getProjects() == null || latestBlock.getProjects().isEmpty()) {
                return ResponseEntity.ok(Map.of("subjects", new Object[0], "students", new Object[0], "cells", new Object[0]));
            }

            Object json = objectMapper.readValue(latestBlock.getProjects(), Object.class);
            return ResponseEntity.ok(json);

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("subjects", new Object[0], "students", new Object[0], "cells", new Object[0]));
        }
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveState(@RequestBody Map<String, Object> statePayload) {
        try {
            String teacherEmail = statePayload.containsKey("email") ? (String) statePayload.get("email") : "marks@system";
            String json = objectMapper.writeValueAsString(statePayload);

            boolean added = blockchainService.addBlock(
                    "MARKS_RECORD", teacherEmail, "Marks",
                    "", "", "", "", "", "", "",
                    "", "", "", "", "", "",
                    "", "", "", "", "",
                    "", "", json
            );

            if (added) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Marks saved to blockchain"));
            } else {
                return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to save marks"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/chain")
    public ResponseEntity<?> getBlocks(@RequestParam(required = false) String email) {
        try {
            List<Block> marksBlocks = blockchainService.getMarksBlocks();
            List<Map<String, Object>> responseList = new ArrayList<>();

            for (Block block : marksBlocks) {
                if (email != null && !email.isEmpty() && !email.equalsIgnoreCase(block.getEmail())) continue;

                Map<String, Object> responseBlock = new HashMap<>();
                responseBlock.put("hash", block.getHash());
                responseBlock.put("previousHash", block.getPreviousHash());
                responseBlock.put("email", block.getEmail());
                
                // --- CRASH-PROOF PARSING ---
                try {
                    String proj = block.getProjects();
                    if (proj != null && !proj.isEmpty()) {
                        responseBlock.put("data", objectMapper.readValue(proj, Object.class));
                    } else {
                        responseBlock.put("data", Map.of()); // Empty data fallback
                    }
                } catch(Exception ex) {
                    responseBlock.put("data", Map.of("error", "Invalid JSON data in this block"));
                }
                
                responseList.add(responseBlock);
            }
            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}