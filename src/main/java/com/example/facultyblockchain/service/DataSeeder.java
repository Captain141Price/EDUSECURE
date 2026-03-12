package com.example.facultyblockchain.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DataSeeder – seeds default users into blockchain .dat files and StudentData.xlsx
 * on application startup, so the app is always usable out of the box even
 * after clearing test data.
 *
 * Default accounts
 * ----------------
 *  Super Admin : username=SuperAdmin,  email=superadmin@college.edu,     pass=super123
 *  Admin       : username=Admin,       email=admin@college.edu,           pass=admin123
 *  Teacher     : username=Teacher,     email=cs23.sahasin.mondal@stcet.ac.in, pass=teacher123
 *  Student     : username=Student,     email=sahasinbravo@gmail.com,      pass=student123
 *                (department=CSE, semester=2, passingYear=2027, classRoll=CSE2)
 */
@Component
public class DataSeeder {

    @Autowired
    private LoginBlockchainService loginBlockchainService;

    @Autowired
    private StudentExcelService studentExcelService;

    @PostConstruct
    public void seedDefaults() {

        // ---------- Super Admin ----------
        if (loginBlockchainService.loadBlockchain("superadmin").isEmpty()) {
            System.out.println("[DataSeeder] Seeding default SuperAdmin...");
            loginBlockchainService.addUser(
                    "SuperAdmin",
                    "superadmin@college.edu",
                    "super123",
                    "superadmin");
        }

        // ---------- Admin ----------
        if (loginBlockchainService.loadBlockchain("admin").isEmpty()) {
            System.out.println("[DataSeeder] Seeding default Admin...");
            loginBlockchainService.addUser(
                    "Admin",
                    "admin@college.edu",
                    "admin123",
                    "admin");
        }

        // ---------- Teacher ----------
        if (loginBlockchainService.loadBlockchain("teacher").isEmpty()) {
            System.out.println("[DataSeeder] Seeding default Teacher...");
            loginBlockchainService.addUser(
                    "Teacher",
                    "cs23.sahasin.mondal@stcet.ac.in",
                    "teacher123",
                    "teacher");
        }

        // ---------- Student ----------
        // Student needs both a blockchain entry AND an Excel entry
        if (loginBlockchainService.loadBlockchain("student").isEmpty()) {
            System.out.println("[DataSeeder] Seeding default Student...");

            // 1. Add to blockchain
            loginBlockchainService.addUser(
                    "Student",
                    "sahasinbravo@gmail.com",
                    "student123",
                    "student");

            // 2. Add to StudentData.xlsx (only if email is not already present)
            if (!studentExcelService.emailExists("sahasinbravo@gmail.com")) {
                // saveStudent(name, email, roll, semester, passingYear, department)
                // classRoll is auto-generated inside saveStudent as rowCount
                studentExcelService.saveStudent(
                        "Student",              // name
                        "sahasinbravo@gmail.com", // email
                        "1234",                 // university roll / student ID
                        "2",                    // semester  (2nd sem  →  2nd year students)
                        "2027",                 // passing year
                        "CSE2");                // department (must be in the allowed list)
            }
        }

        System.out.println("[DataSeeder] Default user seeding complete.");
    }
}
