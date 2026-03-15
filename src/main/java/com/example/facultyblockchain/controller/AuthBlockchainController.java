package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.LoginBlock;
import com.example.facultyblockchain.service.BlockchainService;
import com.example.facultyblockchain.service.LoginBlockchainService;
import com.example.facultyblockchain.service.StudentExcelService;
import com.example.facultyblockchain.service.TeacherMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthBlockchainController {

    @Autowired
    private LoginBlockchainService loginBlockchainService;

    @Autowired
    private StudentExcelService studentExcelService;

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private TeacherMasterService teacherMasterService;

    @Autowired
    private JavaMailSender mailSender;

    // Memory-based session map to track active tokens per user (email)
    // Structure: email -> activeToken
    private static final Map<String, String> activeSessions = new HashMap<>();

    // Map to store temporary OTPs
    // Structure: email -> OTPString:ExpiryTimeInMillis
    private static final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    // LOGIN endpoint (checks specific role blockchain)
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = request.get("email");
        String password = request.get("password");
        String role = request.get("role");

        System.out.println("Login called for role: " + role);
        System.out.println("Name: " + name);
        System.out.println("Email: " + email);

        boolean verified = loginBlockchainService.verifyLogin(name, email, password, role);
        System.out.println("Verification result: " + verified);

        Map<String, Object> response = new HashMap<>();
        if (verified) {
            // Generate simple token for this new session
            String newToken = UUID.randomUUID().toString();
            activeSessions.put(email, newToken);

            response.put("status", "success");
            response.put("message", "Login successful");
            response.put("role", role);
            response.put("sessionToken", newToken);

            if ("student".equalsIgnoreCase(role)) {
                System.out.println("[AUTH DEBUG] Student login - looking up Excel record for email: " + email);
                var studentOpt = studentExcelService.findByEmail(email);
                if (studentOpt.isPresent()) {
                    var student = studentOpt.get();
                    System.out.println("[AUTH DEBUG] Found student: name=" + student.getName()
                            + ", roll=" + student.getRoll()
                            + ", classRoll=" + student.getClassRoll()
                            + ", dept=" + student.getDepartment()
                            + ", semester=" + student.getSemester()
                            + ", passingYear=" + student.getPassingYear());
                    response.put("studentID", student.getRoll());        // University Roll
                    response.put("classRoll", student.getClassRoll());   // Class Roll
                    response.put("department", student.getDepartment());
                    response.put("semester", student.getSemester());
                    response.put("passingYear", student.getPassingYear()); // FIX: was missing
                } else {
                    System.out.println("[AUTH DEBUG] WARNING: Student blockchain login succeeded but NO Excel record found for email: " + email);
                }
            }

            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Invalid credentials or role");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    // Endpoint to verify if a user's session is still valid
    @GetMapping("/verify-session")
    public ResponseEntity<Map<String, Object>> verifySession(@RequestParam String email, @RequestParam String token) {
        Map<String, Object> response = new HashMap<>();

        String currentActiveToken = activeSessions.get(email);

        if (currentActiveToken != null && currentActiveToken.equals(token)) {
            response.put("status", "valid");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "invalid");
            response.put("message", "Session conflict: Logged in from another device");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    // SIGNUP endpoint (adds user to correct blockchain)
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String email = request.get("email");
        String password = request.get("password");
        String role = request.get("role");

        System.out.println("Signup called for role: " + role);

        Map<String, Object> response = new HashMap<>();

        if (loginBlockchainService.userExists(name, email, role)) {
            response.put("status", "error");
            response.put("message", "User already exists in role: " + role);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        boolean success = loginBlockchainService.addUser(name, email, password, role);
        response.put("status", success ? "success" : "error");
        response.put("message",
                success ? "Signup successful for role: " + role : "Error while saving user for role: " + role);

        return success ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // Fetch teacher list (filtered by role and email)
    @GetMapping("/teachers")
    public ResponseEntity<Map<String, Object>> getTeachers(@RequestParam String role, @RequestParam String email) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<LoginBlock> teachers = loginBlockchainService.loadBlockchain("teacher");

            if (role.equalsIgnoreCase("teacher")) {
                // Show only the logged-in teacher
                teachers = teachers.stream()
                        .filter(t -> t.getEmail().equalsIgnoreCase(email))
                        .toList();
            }

            response.put("status", "success");
            response.put("teachers", teachers);
            response.put("role", role);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestParam String email) {
        try {
            activeSessions.remove(email);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Logged out successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE ALL DATA — Super Admin reset for testing/dev.
     * Clears: 5 Block chains, 4 login chains, CourseOffering.dat, StudentData.xlsx.
     * After reset, restart the server so DataSeeder re-seeds default credentials.
     */
    @PostMapping("/reset-all")
    public ResponseEntity<Map<String, String>> resetAll() {
        try {
            // 1. Clear in-memory block chains + their .dat files
            blockchainService.clearAllChains();

            // 2. Clear all login blockchain files (student / teacher / admin / superadmin)
            loginBlockchainService.clearAllLoginChains();

            // 3. Clear CourseOffering.dat (plain-text pipe-delimited file)
            String courseOfferingPath = System.getProperty("user.dir") + "/CourseOffering.dat";
            try (FileWriter fw = new FileWriter(courseOfferingPath, false)) {
                fw.write(""); // overwrite with empty content
            } catch (Exception ignored) {}

            // 4. Delete StudentData.xlsx (will be recreated by StudentExcelService.saveStudent())
            String excelPath = System.getProperty("user.dir") + "/StudentData.xlsx";
            new File(excelPath).delete();

            // 5. Clear active sessions so everyone is forced to log in again
            activeSessions.clear();

            System.out.println("[Reset] All system data cleared by Super Admin.");
            return ResponseEntity.ok(Map.of("status", "success",
                    "message", "All data cleared. Restart the server to restore default credentials."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // --- FORGOT PASSWORD FLOW --- //

    /**
     * Step 1 — Request OTP.
     * Searches ALL role blockchains so both students AND teachers can reset their password.
     * OTP is stored as "otp|expiryMs|role" (pipe-separated to avoid clash with email colons).
     */
    @PostMapping("/forgot-password/generate-otp")
    public ResponseEntity<Map<String, String>> generateOtp(@RequestParam String email) {
        try {
            String role = resolvePasswordResetRole(email);
            if (role == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "error", "message", "Email is not registered in the system."));
            }

            // Generate 6-digit OTP
            SecureRandom random = new SecureRandom();
            String otp = String.valueOf(100000 + random.nextInt(900000));

            long expiryTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes

            // BUG FIX 2: was "otp:expiry" — colon clashes with email format when parsing.
            // Now uses pipe "|" as separator. Also store role so reset step knows which .dat to update.
            otpStorage.put(email.toLowerCase(), otp + "|" + expiryTime + "|" + role);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Password Reset OTP");
            message.setText(
                    "Your OTP for resetting your password is: " + otp +
                    "\n\nThis OTP will expire in 5 minutes." +
                    "\n\nIf you did not request this, please ignore this email.");
            mailSender.send(message);

            return ResponseEntity.ok(Map.of("status", "success", "message", "OTP sent to your email."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    /**
     * Step 2 — Verify OTP.
     * Checks expiry and match, then marks the entry as VERIFIED in storage.
     */
    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String enteredOtp = request.get("otp");

            if (email == null || enteredOtp == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing parameters."));
            }

            String storedData = otpStorage.get(email.toLowerCase());
            if (storedData == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "No OTP requested for this email."));
            }

            // Format: otp|expiryMs|role  (or otp|expiryMs|role|VERIFIED after first verify)
            String[] parts = storedData.split("\\|");
            if (parts.length < 3) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "OTP data corrupted. Please request a new OTP."));
            }

            String storedOtp  = parts[0];
            long expiryTime   = Long.parseLong(parts[1]);
            String role       = parts[2];

            if (System.currentTimeMillis() > expiryTime) {
                otpStorage.remove(email.toLowerCase());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "OTP has expired. Please request a new one."));
            }

            if (!storedOtp.equals(enteredOtp.trim())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "Invalid OTP."));
            }

            // Mark as verified — extend expiry by 10 minutes so the reset step succeeds
            otpStorage.put(email.toLowerCase(),
                    storedOtp + "|" + (System.currentTimeMillis() + 600_000) + "|" + role + "|VERIFIED");

            return ResponseEntity.ok(Map.of("status", "success", "message", "OTP verified."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Step 3 — Reset Password.
     * Reads the role from OTP storage and updates the correct blockchain.
     * Works for students AND teachers.
     */
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email       = request.get("email");
            String enteredOtp  = request.get("otp");
            String newPassword = request.get("newPassword");

            if (email == null || enteredOtp == null || newPassword == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing parameters."));
            }

            if (newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message",
                        "Password must be at least 6 characters."));
            }

            String storedData = otpStorage.get(email.toLowerCase());
            if (storedData == null || !storedData.contains("|VERIFIED")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "OTP not verified or expired. Please start over."));
            }

            // Format: otp|expiryMs|role|VERIFIED
            String[] parts = storedData.split("\\|");
            String storedOtp = parts[0];
            String role      = parts[2];

            if (!storedOtp.equals(enteredOtp.trim())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "Invalid OTP."));
            }

            // BUG FIX 3: was hardcoded to role="student" — teachers never got updated.
            // Now use the role detected during generateOtp().
            // Get the stored name from the blockchain (works for all roles, not just Excel).
            String name = loginBlockchainService.getNameByEmail(email, role);
            if (name == null) {
                if ("teacher".equalsIgnoreCase(role)) {
                    name = teacherMasterService.findByEmail(email)
                            .map(record -> record.getName())
                            .filter(storedName -> storedName != null && !storedName.isBlank())
                            .orElse(null);
                } else if ("student".equalsIgnoreCase(role)) {
                    name = studentExcelService.findByEmail(email)
                            .map(student -> student.getName())
                            .filter(storedName -> storedName != null && !storedName.isBlank())
                            .orElse(null);
                }
            }

            if (name == null) {
                name = email.split("@")[0];
            }

            // addUser() appends a new block; verifyLogin() uses last-block-wins
            // so this effectively overwrites the old password.
            boolean updated = loginBlockchainService.addUser(name, email, newPassword, role);

            if (updated) {
                otpStorage.remove(email.toLowerCase()); // consume OTP
                return ResponseEntity.ok(Map.of("status", "success",
                        "message", "Password reset successfully. You can now log in."));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("status", "error", "message", "Failed to update password in blockchain."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private String resolvePasswordResetRole(String email) {
        String role = loginBlockchainService.findRoleByEmail(email);
        if (role != null) {
            return role;
        }

        if (teacherMasterService.findByEmail(email).isPresent()) {
            return "teacher";
        }

        if (studentExcelService.findByEmail(email).isPresent()) {
            return "student";
        }

        return null;
    }
}
