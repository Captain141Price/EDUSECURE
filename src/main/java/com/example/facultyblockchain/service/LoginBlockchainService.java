package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.LoginBlock;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Service
public class LoginBlockchainService {

    private static final String BASE_PATH = "src/main/resources/blockchain/";
    public static final String DEFAULT_TEACHER_PASSWORD = "teacher@123";
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Load the blockchain based on role
    @SuppressWarnings("unchecked")
    public List<LoginBlock> loadBlockchain(String role) {
        String fileName = getFileName(role);
        File file = new File(BASE_PATH + fileName);
        if (!file.exists())
            return new ArrayList<>();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (List<LoginBlock>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Save blockchain based on role
    private void saveBlockchain(String role, List<LoginBlock> blockchain) {
        String fileName = getFileName(role);
        File dir = new File(BASE_PATH);
        if (!dir.exists())
            dir.mkdirs();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BASE_PATH + fileName))) {
            oos.writeObject(blockchain);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Add a new user to the correct blockchain file
    public boolean addUser(String name, String email, String password, String role) {
        System.out.println("addUser called for role: " + role);
        List<LoginBlock> blockchain = loadBlockchain(role);

        String hashedPassword = encodePassword(password);
        String previousHash = blockchain.isEmpty() ? "0" : blockchain.get(blockchain.size() - 1).getHash();
        LoginBlock block = new LoginBlock(blockchain.size(), name, email, hashedPassword, previousHash);
        blockchain.add(block);

        saveBlockchain(role, blockchain);
        return true;
    }

    public boolean ensureUserIfAbsent(String name, String email, String password, String role) {
        if (emailExistsInRole(email, role)) {
            return false;
        }
        return addUser(name, email, password, role);
    }

    // Verify login credentials against role-specific blockchain
    public boolean verifyLogin(String name, String email, String password, String role) {
        System.out.println("[LOGIN DEBUG] verifyLogin called for role: " + role);
        System.out.println("[LOGIN DEBUG] Looking up email: '" + email + "', provided name: '" + name + "'");

        List<LoginBlock> blockchain = loadBlockchain(role);
        System.out.println("[LOGIN DEBUG] Blockchain chain size for role '" + role + "': " + blockchain.size());

        LoginBlock latestBlock = null;

        // Latest block for this email (last occurrence wins — allows password reset)
        for (LoginBlock block : blockchain) {
            if (block.getEmail().equalsIgnoreCase(email)) {
                latestBlock = block;
            }
        }

        // No user found with this email
        if (latestBlock == null) {
            System.out.println("[LOGIN DEBUG] FAIL: No block found for email: '" + email + "' in role '" + role + "'");
            return false;
        }

        System.out.println("[LOGIN DEBUG] Found block at index=" + latestBlock.getIndex()
                + ", storedName='" + latestBlock.getName()
                + "', storedEmail='" + latestBlock.getEmail() + "'");

        // Verify name (loose check: just ignore case and trim) and password against latest block
        boolean nameMatch = latestBlock.getName().trim().equalsIgnoreCase(name.trim());
        boolean passMatch = matchesPassword(password, latestBlock.getHashedPassword());

        System.out.println("[LOGIN DEBUG] Name match: " + nameMatch
                + " | Stored: '" + latestBlock.getName() + "' vs Provided: '" + name + "'");
        System.out.println("[LOGIN DEBUG] Password match: " + passMatch
                + " | Stored hash: " + latestBlock.getHashedPassword().substring(0, 8) + "..."
                + " | Password verified using compatible hash matcher");

        // FIX: If the name doesn't match perfectly, but the email and password do, let them in.
        // It's a common UX issue for users to mistype their name slightly (e.g. omitting middle name).
        // Since Email is the primary unique identifier and password is correct, we authenticate.
        if (!nameMatch && passMatch) {
            System.out.println("[LOGIN DEBUG] Name mismatch but password correct. Allowing login based on email.");
            nameMatch = true; 
        }

        if (!nameMatch) {
            System.out.println("[LOGIN DEBUG] FAIL reason: Name mismatch. Did student type the correct username?");
        }
        if (!passMatch) {
            System.out.println("[LOGIN DEBUG] FAIL reason: Password hash mismatch.");
        }

        return nameMatch && passMatch;
    }

    // Check if a user already exists in the given role
    public boolean userExists(String name, String email, String role) {
        List<LoginBlock> blockchain = loadBlockchain(role);
        for (LoginBlock block : blockchain) {
            if (block.getName().equals(name) && block.getEmail().equals(email)) {
                return true;
            }
        }
        return false;
    }

    public boolean emailExistsInRole(String email, String role) {
        if (email == null || email.isBlank()) {
            return false;
        }

        for (LoginBlock block : loadBlockchain(role)) {
            if (email.equalsIgnoreCase(block.getEmail())) {
                return true;
            }
        }
        return false;
    }

    // Helper to choose the correct file
    private String getFileName(String role) {
        return switch (role.toLowerCase()) {
            case "teacher" -> "Teacher.dat";
            case "admin" -> "Admin.dat";
            case "superadmin" -> "SuperAdmin.dat";
            case "student" -> "Student.dat";
            default -> throw new IllegalArgumentException("Invalid role: " + role);
        };
    }

    // Hash function (same as your old version)
    private String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error while hashing", e);
        }
    }

    private String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        return storedPassword.equals(hash(rawPassword));
    }

    // public boolean emailExists(String email) {
    // List<LoginBlock> blockchain = loadBlockchain("");
    // return blockchain.getChain().stream()
    // .anyMatch(b -> b.getEmail().equalsIgnoreCase(email));
    // }

    /**
     * Searches all role blockchains (student, teacher, admin, superadmin)
     * to find which role this email belongs to.
     * Returns the role string, or null if the email is not registered anywhere.
     */
    public String findRoleByEmail(String email) {
        for (String role : List.of("student", "teacher", "admin", "superadmin")) {
            try {
                List<LoginBlock> chain = loadBlockchain(role);
                for (LoginBlock block : chain) {
                    if (block.getEmail().equalsIgnoreCase(email)) {
                        return role;
                    }
                }
            } catch (Exception ignored) {
                // role file may not exist yet — skip
            }
        }
        return null; // not found in any blockchain
    }

    /**
     * Returns the stored name for the first (or last) block matching the email
     * in the given role's blockchain. Returns null if not found.
     */
    public String getNameByEmail(String email, String role) {
        List<LoginBlock> chain = loadBlockchain(role);
        String name = null;
        for (LoginBlock block : chain) {
            if (block.getEmail().equalsIgnoreCase(email)) {
                name = block.getName(); // last match wins (same as verifyLogin)
            }
        }
        return name;
    }

    /**
     * Clears ALL login blockchain files (Student, Teacher, Admin, SuperAdmin).
     * DataSeeder will re-seed defaults on the next server restart.
     */
    public void clearAllLoginChains() {
        for (String role : List.of("student", "teacher", "admin", "superadmin")) {
            saveBlockchain(role, new ArrayList<>());
        }
        System.out.println("[Reset] All login blockchain chains cleared.");
    }
}
