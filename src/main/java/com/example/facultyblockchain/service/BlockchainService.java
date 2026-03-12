package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.Block;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlockchainService {

    // --- FIVE SEPARATE CHAINS ---
    private final List<Block> facultyChain = new ArrayList<>();
    private final List<Block> attendanceChain = new ArrayList<>();
    private final List<Block> marksChain = new ArrayList<>();
    private final List<Block> marChain = new ArrayList<>();   // <-- NEW
    private final List<Block> moocsChain = new ArrayList<>(); // <-- NEW
    
    private final int difficulty = 3;
    
    // --- FIVE SEPARATE FILES ---
    private static final String FACULTY_FILE_PATH = System.getProperty("user.dir") + "/FacultyBlockchain.dat";
    private static final String ATTENDANCE_FILE_PATH = System.getProperty("user.dir") + "/AttendanceBlockchain.dat";
    private static final String MARKS_FILE_PATH = System.getProperty("user.dir") + "/MarksBlockchain.dat";
    private static final String MAR_FILE_PATH = System.getProperty("user.dir") + "/MarBlockchain.dat";     // <-- NEW
    private static final String MOOCS_FILE_PATH = System.getProperty("user.dir") + "/MoocsBlockchain.dat"; // <-- NEW

    public BlockchainService() {
        loadBlockchains();
    }

    public boolean addBlock(
            String name, String email, String department, String dateOfJoining,
            String dateOfAssign, String specialization, String teachingExp, String qualifications,
            String publications, String rawBibtex, String research,
            String publicationTitle, String publicationAuthors, String journal,
            String volume, String number, String pages, String year,
            String publisher, String organization, String booktitle,
            String professionalActivity, String facultyParticipation, String projects
    ) {
        try {
            // ROUTING LOGIC: Where does this block belong?
            List<Block> targetChain;
            String targetFilePath;

            if ("ATTENDANCE_RECORD".equals(name)) {
                targetChain = attendanceChain;
                targetFilePath = ATTENDANCE_FILE_PATH;
            } else if ("MARKS_RECORD".equals(name)) {
                targetChain = marksChain;
                targetFilePath = MARKS_FILE_PATH;
            } else if ("MAR_RECORD".equals(name)) {
                targetChain = marChain;
                targetFilePath = MAR_FILE_PATH;
            } else if ("MOOCS_RECORD".equals(name)) {
                targetChain = moocsChain;
                targetFilePath = MOOCS_FILE_PATH;
            } else {
                targetChain = facultyChain;
                targetFilePath = FACULTY_FILE_PATH;
            }

            String prevHash = targetChain.isEmpty() ? "0" : targetChain.get(targetChain.size() - 1).getHash();

            Block newBlock = new Block(
                    name, email, department, dateOfJoining, dateOfAssign,
                    specialization, teachingExp, qualifications,
                    publications, rawBibtex, research,
                    publicationTitle, publicationAuthors, journal,
                    volume, number, pages, year,
                    publisher, organization, booktitle,
                    professionalActivity, facultyParticipation, projects,
                    prevHash
            );

            newBlock.mineBlock(difficulty);
            targetChain.add(newBlock);
            saveChain(targetChain, targetFilePath);

            System.out.println("Block added for: " + name + " (" + email + ") -> Saved to " + targetFilePath);
            return true;
        } catch (Exception e) {
            System.err.println("Error adding block: " + e.getMessage());
            return false;
        }
    }

    public boolean userExists(String name, String email) {
        return facultyChain.stream().anyMatch(b -> b.getName().equalsIgnoreCase(name) && b.getEmail().equalsIgnoreCase(email));
    }

    public List<Block> getFacultyBlocks() { return new ArrayList<>(facultyChain); }
    public List<Block> getAttendanceBlocks() { return new ArrayList<>(attendanceChain); }
    public List<Block> getMarksBlocks() { return new ArrayList<>(marksChain); }
    public List<Block> getMarBlocks() { return new ArrayList<>(marChain); }     // <-- NEW
    public List<Block> getMoocsBlocks() { return new ArrayList<>(moocsChain); } // <-- NEW

    public List<Block> getAllBlocks() {
        List<Block> combined = new ArrayList<>();
        combined.addAll(facultyChain);
        combined.addAll(attendanceChain);
        combined.addAll(marksChain);
        combined.addAll(marChain);
        combined.addAll(moocsChain);
        return combined;
    }

    public Block searchByName(String name) {
        return facultyChain.stream().filter(b -> b.getName().equalsIgnoreCase(name)).reduce((first, second) -> second).orElse(null);
    }

    public Block searchByEmail(String email) {
        return facultyChain.stream().filter(b -> b.getEmail().equalsIgnoreCase(email)).reduce((first, second) -> second).orElse(null);
    }

    public boolean updateFaculty(Block updated) {
        try {
            String prevHash = facultyChain.isEmpty() ? "0" : facultyChain.get(facultyChain.size() - 1).getHash();
            Block newBlock = new Block(
                    updated.getName(), updated.getEmail(), updated.getDepartment(), updated.getDateOfJoining(), updated.getDateOfAssign(),
                    updated.getSpecialization(), updated.getTeachingExp(), updated.getQualifications(), updated.getPublications(), updated.getRawBibtex(), updated.getResearch(),
                    updated.getPublicationTitle(), updated.getPublicationAuthors(), updated.getJournal(), updated.getVolume(), updated.getNumber(), updated.getPages(), updated.getYear(),
                    updated.getPublisher(), updated.getOrganization(), updated.getBooktitle(), updated.getProfessionalActivity(), updated.getFacultyParticipation(), updated.getProjects(),
                    prevHash
            );
            newBlock.mineBlock(difficulty);
            facultyChain.add(newBlock);
            saveChain(facultyChain, FACULTY_FILE_PATH);
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean isChainValid() {
        return validateSpecificChain(facultyChain, "Faculty") && 
               validateSpecificChain(attendanceChain, "Attendance") &&
               validateSpecificChain(marksChain, "Marks") &&
               validateSpecificChain(marChain, "MAR") &&
               validateSpecificChain(moocsChain, "MOOCs");
    }

    private boolean validateSpecificChain(List<Block> chain, String chainName) {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);
            if (!current.getHash().equals(current.calculateHash())) return false;
            if (!current.getPreviousHash().equals(previous.getHash())) return false;
        }
        return true;
    }

    private void saveChain(List<Block> chain, String filePath) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath))) {
            out.writeObject(chain);
        } catch (IOException e) { System.err.println("Error saving: " + e.getMessage()); }
    }

    /**
     * Clears ALL blockchain data for the 5 Block chains (Faculty, Attendance, Marks, MAR, MOOCs).
     * Both the in-memory lists AND the .dat files are cleared so no stale data remains in memory.
     */
    public void clearAllChains() {
        facultyChain.clear();
        attendanceChain.clear();
        marksChain.clear();
        marChain.clear();
        moocsChain.clear();

        saveChain(facultyChain, FACULTY_FILE_PATH);
        saveChain(attendanceChain, ATTENDANCE_FILE_PATH);
        saveChain(marksChain, MARKS_FILE_PATH);
        saveChain(marChain, MAR_FILE_PATH);
        saveChain(moocsChain, MOOCS_FILE_PATH);

        System.out.println("[Reset] All Block blockchain chains cleared.");
    }

    private void loadBlockchains() {
        loadSpecificChain(facultyChain, FACULTY_FILE_PATH, "Faculty");
        loadSpecificChain(attendanceChain, ATTENDANCE_FILE_PATH, "Attendance");
        loadSpecificChain(marksChain, MARKS_FILE_PATH, "Marks");
        loadSpecificChain(marChain, MAR_FILE_PATH, "MAR");       // <-- NEW
        loadSpecificChain(moocsChain, MOOCS_FILE_PATH, "MOOCs"); // <-- NEW
    }

    @SuppressWarnings("unchecked")
    private void loadSpecificChain(List<Block> chain, String filePath, String chainName) {
        File file = new File(filePath);
        if (!file.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            List<Block> loaded = (List<Block>) in.readObject();
            chain.clear();
            chain.addAll(loaded);
        } catch (Exception e) {}
    }
}