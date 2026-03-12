package com.example.facultyblockchain.service;

import java.io.*;
import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class MentorAssignmentService {

    private static final String MENTOR_ASSIGNMENT_FILE = System.getProperty("user.dir") + "/MentorAssignment.dat";
    private static final String SEPARATOR = "\\|";

    /**
     * Map of teacher email (lowercase) to their assigned data.
     * Value format: "Dept|Semester|StartRoll|EndRoll"
     */
    private Map<String, String> assignments = new HashMap<>();

    public MentorAssignmentService() {
        loadAssignments();
    }

    private synchronized void loadAssignments() {
        assignments.clear();
        File file = new File(MENTOR_ASSIGNMENT_FILE);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(SEPARATOR);
                if (parts.length == 5) {
                    // email | department | semester | startRoll | endRoll
                    assignments.put(parts[0].trim().toLowerCase(), 
                        parts[1].trim() + "|" + 
                        parts[2].trim() + "|" + 
                        parts[3].trim() + "|" + 
                        parts[4].trim()
                    );
                } else if (parts.length == 3) {
                    // legacy fallback
                    assignments.put(parts[0].trim().toLowerCase(), parts[1].trim() + "|" + parts[2].trim() + "|0|9999");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveAssignments() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(MENTOR_ASSIGNMENT_FILE))) {
            for (Map.Entry<String, String> entry : assignments.entrySet()) {
                bw.write(entry.getKey() + "|" + entry.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Assigns a teacher to act as a mentor for a specific department, semester, and roll range.
     * Only 1 assignment per teacher is supported at a time.
     */
    public synchronized void assignMentor(String teacherEmail, String department, String semester, int startRoll, int endRoll) {
        if (teacherEmail == null || teacherEmail.isEmpty()) return;
        assignments.put(teacherEmail.trim().toLowerCase(), 
            department.trim() + "|" + semester.trim() + "|" + startRoll + "|" + endRoll);
        saveAssignments();
    }

    /**
     * Returns the assigned department, semester, and roll bounds for a given teacher email.
     * Returns null if unassigned.
     */
    public Map<String, String> getMentorAssignment(String teacherEmail) {
        if (teacherEmail == null) return null;
        String val = assignments.get(teacherEmail.trim().toLowerCase());
        if (val == null) return null;

        String[] parts = val.split(SEPARATOR);
        if (parts.length < 2) return null;

        Map<String, String> result = new HashMap<>();
        result.put("department", parts[0]);
        result.put("semester", parts[1]);
        if (parts.length == 4) {
            result.put("startRoll", parts[2]);
            result.put("endRoll", parts[3]);
        } else {
            result.put("startRoll", "0");
            result.put("endRoll", "9999");
        }
        return result;
    }

    /**
     * Returns all mentor assignments (useful for admin displays).
     */
    public Map<String, Map<String, String>> getAllAssignments() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String email : assignments.keySet()) {
            result.put(email, getMentorAssignment(email));
        }
        return result;
    }
}
