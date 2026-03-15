package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.model.TeacherMasterRecord;
import com.example.facultyblockchain.service.BlockchainService;
import com.example.facultyblockchain.service.EmailService;
import com.example.facultyblockchain.service.LoginBlockchainService;
import com.example.facultyblockchain.service.TeacherMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BlockchainController {

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TeacherMasterService teacherMasterService;

    @Autowired
    private LoginBlockchainService loginBlockchainService;

    // Add a new faculty block (with formatted & raw BibTeX)

    @PostMapping("/add")
    public Map<String, Object> addFaculty(
            @RequestParam(required = false) String teacherId,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String department,
            @RequestParam String dateOfJoining,
            @RequestParam(required = false) String dateOfAssign,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String teachingExp,
            @RequestParam(required = false) String qualifications,
            @RequestParam(required = false) String publications,      // formatted BibTeX
            @RequestParam(required = false) String rawBibtex,         // NEW: raw BibTeX input
            @RequestParam(required = false) String research,
            @RequestParam(required = false) String publicationTitle,
            @RequestParam(required = false) String publicationAuthors,
            @RequestParam(required = false) String journal,
            @RequestParam(required = false) String volume,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String pages,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String booktitle,
            @RequestParam(required = false) String professionalActivity,
            @RequestParam(required = false) String facultyParticipation,
            @RequestParam(required = false) String projects
    ) {
        Map<String, Object> response = new HashMap<>();

        if (blockchainService.userExists(name, email)) {
            response.put("status", "error");
            response.put("message", "Faculty already exists!");
            return response;
        }

        boolean success = blockchainService.addBlock(
                name, email, department, dateOfJoining,
                dateOfAssign, specialization, teachingExp, qualifications,
                publications, rawBibtex, research,
                publicationTitle, publicationAuthors, journal,
                volume, number, pages, year,
                publisher, organization, booktitle,
                professionalActivity, facultyParticipation, projects
        );

        if (success) {
            TeacherMasterRecord masterRecord = new TeacherMasterRecord();
            masterRecord.setTeacherId(teacherId);
            masterRecord.setName(name);
            masterRecord.setEmail(email);
            masterRecord.setDepartment(department);
            masterRecord.setStatus("Active");
            masterRecord.setSpecialization(specialization);
            masterRecord.setTeachingExperience(teachingExp);
            masterRecord.setQualifications(qualifications);
            masterRecord.setDateOfJoining(dateOfJoining);
            masterRecord.setDateOfAssign(dateOfAssign);
            masterRecord.setResearchDetails(research);
            masterRecord.setProfessionalActivities(professionalActivity);
            masterRecord.setParticipation(facultyParticipation);
            masterRecord.setProjects(projects);
            masterRecord.setPublications(publications);
            masterRecord.setRawBibtex(rawBibtex);
            teacherMasterService.saveTeacher(masterRecord);
            loginBlockchainService.ensureUserIfAbsent(name, email, LoginBlockchainService.DEFAULT_TEACHER_PASSWORD, "teacher");
        }

        response.put("status", success ? "success" : "error");
        response.put("message", success ? "Faculty added successfully!" : "Failed to add faculty.");
        return response;
    }

    // Getting latest faculty record by email

    @GetMapping("/get")
    public Object getFaculty(@RequestParam String email) {
        var masterRecord = teacherMasterService.findByEmail(email);
        if (masterRecord.isPresent()) {
            return masterRecord.get();
        }
        Block block = blockchainService.searchByEmail(email);
        if (block == null) {
            return Map.of("status", "error", "message", "Faculty not found!");
        }
        return block;
    }

    // Getting all blocks

    @GetMapping("/all")
    public List<Block> getAllBlocks() {
        return blockchainService.getAllBlocks();
    }

    /**
     * Update Faculty Profile (adds a new block version)
     * Includes rawBibtex field.
     */

    @PostMapping("/update")
    public Map<String, Object> updateFaculty(
            @RequestParam String email,
            @RequestParam(required = false) String teacherId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String department,
            @RequestParam String dateOfJoining,
            @RequestParam(required = false) String dateOfAssign,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String teachingExp,
            @RequestParam(required = false) String qualifications,
            @RequestParam(required = false) String publications,
            @RequestParam(required = false) String rawBibtex,  // NEW field for update
            @RequestParam(required = false) String research,
            @RequestParam(required = false) String publicationTitle,
            @RequestParam(required = false) String publicationAuthors,
            @RequestParam(required = false) String journal,
            @RequestParam(required = false) String volume,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String pages,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String booktitle,
            @RequestParam(required = false) String professionalActivity,
            @RequestParam(required = false) String facultyParticipation,
            @RequestParam(required = false) String projects
    ) {
        Map<String, Object> response = new HashMap<>();

        Block existing = blockchainService.searchByEmail(email);
        if (existing == null) {
            response.put("status", "error");
            response.put("message", "Faculty not found. Cannot update!");
            return response;
        }

        // Preserve previous values if not provided
        if (name == null || name.isEmpty()) name = existing.getName();
        if (department == null || department.isEmpty()) department = existing.getDepartment();
        if (dateOfAssign == null) dateOfAssign = existing.getDateOfAssign();
        if (specialization == null) specialization = existing.getSpecialization();
        if (teachingExp == null) teachingExp = existing.getTeachingExp();
        if (qualifications == null) qualifications = existing.getQualifications();
        if (publications == null) publications = existing.getPublications();
        if (rawBibtex == null) rawBibtex = existing.getRawBibtex();
        if (research == null) research = existing.getResearch();
        if (publicationTitle == null) publicationTitle = existing.getPublicationTitle();
        if (publicationAuthors == null) publicationAuthors = existing.getPublicationAuthors();
        if (journal == null) journal = existing.getJournal();
        if (volume == null) volume = existing.getVolume();
        if (number == null) number = existing.getNumber();
        if (pages == null) pages = existing.getPages();
        if (year == null) year = existing.getYear();
        if (publisher == null) publisher = existing.getPublisher();
        if (organization == null) organization = existing.getOrganization();
        if (booktitle == null) booktitle = existing.getBooktitle();
        if (professionalActivity == null) professionalActivity = existing.getProfessionalActivity();
        if (facultyParticipation == null) facultyParticipation = existing.getFacultyParticipation();
        if (projects == null) projects = existing.getProjects();

        boolean success = blockchainService.addBlock(
                name, email, department, dateOfJoining,
                dateOfAssign, specialization, teachingExp, qualifications,
                publications, rawBibtex, research,
                publicationTitle, publicationAuthors, journal,
                volume, number, pages, year,
                publisher, organization, booktitle,
                professionalActivity, facultyParticipation, projects
        );

        if (success) {
            TeacherMasterRecord masterRecord = new TeacherMasterRecord();
            masterRecord.setTeacherId(teacherId);
            masterRecord.setName(name);
            masterRecord.setEmail(email);
            masterRecord.setDepartment(department);
            masterRecord.setStatus("Active");
            masterRecord.setSpecialization(specialization);
            masterRecord.setTeachingExperience(teachingExp);
            masterRecord.setQualifications(qualifications);
            masterRecord.setDateOfJoining(dateOfJoining);
            masterRecord.setDateOfAssign(dateOfAssign);
            masterRecord.setResearchDetails(research);
            masterRecord.setProfessionalActivities(professionalActivity);
            masterRecord.setParticipation(facultyParticipation);
            masterRecord.setProjects(projects);
            masterRecord.setPublications(publications);
            masterRecord.setRawBibtex(rawBibtex);
            teacherMasterService.saveTeacher(masterRecord);
        }

        response.put("status", success ? "success" : "error");
        response.put("message", success
                ? "Profile updated successfully and added to blockchain."
                : "Update failed. Try again.");
        return response;
    }

    // Validate Blockchain Integrity

    @GetMapping("/valid")
    public Map<String, String> checkValidity() {
        boolean valid = blockchainService.isChainValid();
        Map<String, String> response = new HashMap<>();
        response.put("status", valid ? "valid" : "invalid");
        response.put("message", valid
                ? "Blockchain is valid and secure."
                : "Blockchain integrity check failed!");
        return response;
    }

    // Send OTP to email

    @PostMapping("/send-otp")
    public Map<String, Object> sendOTP(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            int otp = emailService.sendOTP(email);
            com.example.facultyblockchain.util.OTPStore.storeOTP(email, otp);
            response.put("status", "success");
            response.put("message", "OTP sent successfully to " + email);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to send OTP: " + e.getMessage());
        }
        return response;
    }

    // Verify OTP

    @GetMapping("/verify-otp")
    public Map<String, Object> verifyOTP(@RequestParam String email, @RequestParam int otp) {
        Map<String, Object> response = new HashMap<>();
        boolean valid = com.example.facultyblockchain.util.OTPStore.verifyOTP(email, otp);
        response.put("status", valid ? "success" : "error");
        response.put("message", valid ? "OTP verified successfully!" : "Invalid or expired OTP.");
        return response;
    }
}
