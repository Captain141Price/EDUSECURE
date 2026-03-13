package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.model.StudentExcelModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manual SuperAdmin archive flow.
 *
 * The action archives only alumni-ready students:
 *   passingYear = selected session
 *   semester    = 8
 *
 * Active storage is preserved for current students; only the selected alumni
 * cohort is appended to archive chains and removed from the active registry.
 */
@Service
public class SemesterRolloverService {

    private static final String BASE = System.getProperty("user.dir") + "/";
    private static final String ARCHIVE_STUDENTS_FILE = BASE + "archive_students.dat";
    private static final String ARCHIVE_MARKS_FILE = BASE + "archive_marks.dat";
    private static final String ARCHIVE_ATTENDANCE_FILE = BASE + "archive_attendance.dat";
    private static final String ARCHIVE_MAR_FILE = BASE + "archive_mar.dat";
    private static final String ARCHIVE_MOOCS_FILE = BASE + "archive_moocs.dat";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private StudentExcelService studentExcelService;

    public RolloverResult performRollover(String passingYear) {
        String normalizedPassingYear = passingYear == null ? "" : passingYear.trim();
        if (normalizedPassingYear.isEmpty()) {
            return RolloverResult.failure("Passing year is required.");
        }

        List<StudentExcelModel> targetStudents =
                studentExcelService.filterStudents(null, normalizedPassingYear, "8");

        if (targetStudents.isEmpty()) {
            return RolloverResult.failure(
                    "No active semester-8 students found for passing year " + normalizedPassingYear + ".");
        }

        Set<String> targetEmails = targetStudents.stream()
                .map(StudentExcelModel::getEmail)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder log = new StringBuilder();
        log.append("End of Session Archive started for passing year ")
                .append(normalizedPassingYear)
                .append('\n');
        log.append("Eligible semester-8 students: ").append(targetStudents.size()).append('\n');

        try {
            appendArchivedStudents(targetStudents);
            log.append("Archived student profiles: ").append(targetStudents.size()).append('\n');

            SplitChainResult marksSplit = splitMarksChain(blockchainService.getMarksBlocks(), targetEmails);
            SplitChainResult attendanceSplit = splitAttendanceChain(blockchainService.getAttendanceBlocks(), targetEmails);
            SplitChainResult marSplit = splitMarChain(blockchainService.getMarBlocks(), targetEmails);
            SplitChainResult moocsSplit = splitWholeStudentChain(blockchainService.getMoocsBlocks(), targetEmails);

            appendBlocks(ARCHIVE_MARKS_FILE, marksSplit.archivedSnapshots);
            appendBlocks(ARCHIVE_ATTENDANCE_FILE, attendanceSplit.archivedSnapshots);
            appendBlocks(ARCHIVE_MAR_FILE, marSplit.archivedSnapshots);
            appendBlocks(ARCHIVE_MOOCS_FILE, moocsSplit.archivedSnapshots);

            blockchainService.overwriteStudentChains(
                    rebuildChain(attendanceSplit.activeSnapshots),
                    rebuildChain(marksSplit.activeSnapshots),
                    rebuildMarChain(marSplit.activeSnapshots),
                    rebuildChain(moocsSplit.activeSnapshots));

            List<StudentExcelModel> removedStudents =
                    studentExcelService.removeStudentsForArchive(normalizedPassingYear, "8");

            log.append("Archived marks blocks: ").append(marksSplit.archivedSnapshots.size()).append('\n');
            log.append("Archived attendance blocks: ").append(attendanceSplit.archivedSnapshots.size()).append('\n');
            log.append("Archived MAR blocks: ").append(marSplit.archivedSnapshots.size()).append('\n');
            log.append("Archived MOOCs blocks: ").append(moocsSplit.archivedSnapshots.size()).append('\n');
            log.append("Removed from active student registry: ").append(removedStudents.size()).append('\n');
            log.append("Archive complete for passing year ").append(normalizedPassingYear);

            return RolloverResult.success(normalizedPassingYear, log.toString());
        } catch (Exception e) {
            return RolloverResult.failure("Archive failed: " + e.getMessage());
        }
    }

    private void appendArchivedStudents(List<StudentExcelModel> newStudents) throws IOException, ClassNotFoundException {
        List<StudentExcelModel> existing = loadStudentProfiles(ARCHIVE_STUDENTS_FILE);
        Map<String, StudentExcelModel> deduped = new LinkedHashMap<>();

        for (StudentExcelModel student : existing) {
            deduped.put(buildStudentKey(student), student);
        }
        for (StudentExcelModel student : newStudents) {
            deduped.put(buildStudentKey(student), student);
        }

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(ARCHIVE_STUDENTS_FILE))) {
            out.writeObject(new ArrayList<>(deduped.values()));
        }
    }

    private String buildStudentKey(StudentExcelModel student) {
        return (student.getRoll() == null ? "" : student.getRoll().trim()) + "|" +
                (student.getEmail() == null ? "" : student.getEmail().trim().toLowerCase());
    }

    private SplitChainResult splitMarksChain(List<Block> chain, Set<String> targetEmails) throws IOException {
        List<BlockSnapshot> activeSnapshots = new ArrayList<>();
        List<BlockSnapshot> archivedSnapshots = new ArrayList<>();

        for (Block block : chain) {
            if (block.getProjects() == null || block.getProjects().isEmpty()) {
                activeSnapshots.add(BlockSnapshot.from(block));
                continue;
            }

            Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
            Object studentsObj = payload.get("students");
            if (!(studentsObj instanceof List<?> studentsList)) {
                activeSnapshots.add(BlockSnapshot.from(block));
                continue;
            }

            List<Map<String, Object>> students = castStudentRows(studentsList);
            List<Map<String, Object>> archivedStudents = students.stream()
                    .filter(student -> targetEmails.contains(readEmail(student)))
                    .toList();
            List<Map<String, Object>> activeStudents = students.stream()
                    .filter(student -> !targetEmails.contains(readEmail(student)))
                    .toList();

            if (!archivedStudents.isEmpty()) {
                Map<String, Object> archivedPayload = new LinkedHashMap<>(payload);
                archivedPayload.put("students", archivedStudents);
                archivedSnapshots.add(BlockSnapshot.from(block, objectMapper.writeValueAsString(archivedPayload)));
            }
            if (!activeStudents.isEmpty()) {
                Map<String, Object> activePayload = new LinkedHashMap<>(payload);
                activePayload.put("students", activeStudents);
                activeSnapshots.add(BlockSnapshot.from(block, objectMapper.writeValueAsString(activePayload)));
            }
        }

        return new SplitChainResult(activeSnapshots, archivedSnapshots);
    }

    private SplitChainResult splitAttendanceChain(List<Block> chain, Set<String> targetEmails) throws IOException {
        return splitMarksChain(chain, targetEmails);
    }

    private SplitChainResult splitWholeStudentChain(List<Block> chain, Set<String> targetEmails) {
        List<BlockSnapshot> activeSnapshots = new ArrayList<>();
        List<BlockSnapshot> archivedSnapshots = new ArrayList<>();

        for (Block block : chain) {
            String blockEmail = block.getEmail() == null ? "" : block.getEmail().trim().toLowerCase();
            if (targetEmails.contains(blockEmail)) {
                archivedSnapshots.add(BlockSnapshot.from(block));
            } else {
                activeSnapshots.add(BlockSnapshot.from(block));
            }
        }

        return new SplitChainResult(activeSnapshots, archivedSnapshots);
    }

    private SplitChainResult splitMarChain(List<Block> chain, Set<String> targetEmails) throws IOException {
        Set<String> archivedSubmissionHashes = new HashSet<>();
        Set<String> activeSubmissionHashes = new HashSet<>();
        List<BlockSnapshot> archivedSnapshots = new ArrayList<>();
        List<BlockSnapshot> activeSnapshots = new ArrayList<>();
        List<Block> reviewBlocks = new ArrayList<>();

        for (Block block : chain) {
            if (block.getProjects() == null || block.getProjects().isEmpty()) {
                activeSnapshots.add(BlockSnapshot.from(block));
                continue;
            }

            Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
            if ("MENTOR_REVIEW".equals(payload.get("action"))) {
                reviewBlocks.add(block);
                continue;
            }

            String blockEmail = block.getEmail() == null ? "" : block.getEmail().trim().toLowerCase();
            if (targetEmails.contains(blockEmail)) {
                archivedSnapshots.add(BlockSnapshot.from(block));
                archivedSubmissionHashes.add(block.getHash());
            } else {
                activeSnapshots.add(BlockSnapshot.from(block));
                activeSubmissionHashes.add(block.getHash());
            }
        }

        for (Block reviewBlock : reviewBlocks) {
            Map<String, Object> payload = objectMapper.readValue(reviewBlock.getProjects(), new TypeReference<>() {});
            String originalHash = Objects.toString(payload.get("originalHash"), "");
            if (archivedSubmissionHashes.contains(originalHash)) {
                archivedSnapshots.add(BlockSnapshot.from(reviewBlock));
            } else if (activeSubmissionHashes.contains(originalHash)) {
                activeSnapshots.add(BlockSnapshot.from(reviewBlock));
            }
        }

        return new SplitChainResult(activeSnapshots, archivedSnapshots);
    }

    private List<Map<String, Object>> castStudentRows(List<?> students) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object student : students) {
            if (student instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    row.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String readEmail(Map<String, Object> student) {
        Object email = student.get("email");
        return email == null ? "" : email.toString().trim().toLowerCase();
    }

    private List<Block> rebuildChain(List<BlockSnapshot> snapshots) {
        List<Block> rebuilt = new ArrayList<>();
        for (BlockSnapshot snapshot : snapshots) {
            String previousHash = rebuilt.isEmpty() ? "0" : rebuilt.get(rebuilt.size() - 1).getHash();
            rebuilt.add(snapshot.toBlock(previousHash));
        }
        return rebuilt;
    }

    private List<Block> rebuildMarChain(List<BlockSnapshot> snapshots) throws IOException {
        List<Block> rebuilt = new ArrayList<>();
        Map<String, String> remappedSubmissionHashes = new HashMap<>();

        for (BlockSnapshot snapshot : snapshots) {
            String previousHash = rebuilt.isEmpty() ? "0" : rebuilt.get(rebuilt.size() - 1).getHash();

            if (!snapshot.isMentorReview()) {
                Block rebuiltBlock = snapshot.toBlock(previousHash);
                rebuilt.add(rebuiltBlock);
                remappedSubmissionHashes.put(snapshot.originalHash, rebuiltBlock.getHash());
                continue;
            }

            Map<String, Object> payload = objectMapper.readValue(snapshot.projects, new TypeReference<>() {});
            String originalHash = Objects.toString(payload.get("originalHash"), "");
            if (remappedSubmissionHashes.containsKey(originalHash)) {
                payload.put("originalHash", remappedSubmissionHashes.get(originalHash));
            }

            Block rebuiltReview = snapshot.toBlock(previousHash, objectMapper.writeValueAsString(payload));
            rebuilt.add(rebuiltReview);
        }

        return rebuilt;
    }

    private void appendBlocks(String archiveFilePath, List<BlockSnapshot> newSnapshots)
            throws IOException, ClassNotFoundException {
        if (newSnapshots.isEmpty()) {
            return;
        }

        List<Block> archivedBlocks = loadBlocks(archiveFilePath);
        Map<String, String> remappedSubmissionHashes = new HashMap<>();

        for (BlockSnapshot snapshot : newSnapshots) {
            String previousHash = archivedBlocks.isEmpty() ? "0" : archivedBlocks.get(archivedBlocks.size() - 1).getHash();
            Block nextBlock;

            if (snapshot.isMentorReview()) {
                Map<String, Object> payload = objectMapper.readValue(snapshot.projects, new TypeReference<>() {});
                String originalHash = Objects.toString(payload.get("originalHash"), "");
                if (remappedSubmissionHashes.containsKey(originalHash)) {
                    payload.put("originalHash", remappedSubmissionHashes.get(originalHash));
                }
                nextBlock = snapshot.toBlock(previousHash, objectMapper.writeValueAsString(payload));
            } else {
                nextBlock = snapshot.toBlock(previousHash);
                if ("MAR_RECORD".equals(snapshot.name)) {
                    remappedSubmissionHashes.put(snapshot.originalHash, nextBlock.getHash());
                }
            }

            archivedBlocks.add(nextBlock);
        }

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(archiveFilePath))) {
            out.writeObject(archivedBlocks);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Block> loadBlocks(String path) throws IOException, ClassNotFoundException {
        File file = new File(path);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (List<Block>) in.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    private List<StudentExcelModel> loadStudentProfiles(String path) throws IOException, ClassNotFoundException {
        File file = new File(path);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (List<StudentExcelModel>) in.readObject();
        }
    }

    private static class SplitChainResult {
        final List<BlockSnapshot> activeSnapshots;
        final List<BlockSnapshot> archivedSnapshots;

        SplitChainResult(List<BlockSnapshot> activeSnapshots, List<BlockSnapshot> archivedSnapshots) {
            this.activeSnapshots = activeSnapshots;
            this.archivedSnapshots = archivedSnapshots;
        }
    }

    private static class BlockSnapshot {
        final String name;
        final String email;
        final String department;
        final String dateOfJoining;
        final String dateOfAssign;
        final String specialization;
        final String teachingExp;
        final String qualifications;
        final String publications;
        final String rawBibtex;
        final String research;
        final String publicationTitle;
        final String publicationAuthors;
        final String journal;
        final String volume;
        final String number;
        final String pages;
        final String year;
        final String publisher;
        final String organization;
        final String booktitle;
        final String professionalActivity;
        final String facultyParticipation;
        final String projects;
        final String originalHash;

        private BlockSnapshot(
                String name,
                String email,
                String department,
                String dateOfJoining,
                String dateOfAssign,
                String specialization,
                String teachingExp,
                String qualifications,
                String publications,
                String rawBibtex,
                String research,
                String publicationTitle,
                String publicationAuthors,
                String journal,
                String volume,
                String number,
                String pages,
                String year,
                String publisher,
                String organization,
                String booktitle,
                String professionalActivity,
                String facultyParticipation,
                String projects,
                String originalHash) {
            this.name = name;
            this.email = email;
            this.department = department;
            this.dateOfJoining = dateOfJoining;
            this.dateOfAssign = dateOfAssign;
            this.specialization = specialization;
            this.teachingExp = teachingExp;
            this.qualifications = qualifications;
            this.publications = publications;
            this.rawBibtex = rawBibtex;
            this.research = research;
            this.publicationTitle = publicationTitle;
            this.publicationAuthors = publicationAuthors;
            this.journal = journal;
            this.volume = volume;
            this.number = number;
            this.pages = pages;
            this.year = year;
            this.publisher = publisher;
            this.organization = organization;
            this.booktitle = booktitle;
            this.professionalActivity = professionalActivity;
            this.facultyParticipation = facultyParticipation;
            this.projects = projects;
            this.originalHash = originalHash;
        }

        static BlockSnapshot from(Block block) {
            return from(block, block.getProjects());
        }

        static BlockSnapshot from(Block block, String projects) {
            return new BlockSnapshot(
                    block.getName(),
                    block.getEmail(),
                    block.getDepartment(),
                    block.getDateOfJoining(),
                    block.getDateOfAssign(),
                    block.getSpecialization(),
                    block.getTeachingExp(),
                    block.getQualifications(),
                    block.getPublications(),
                    block.getRawBibtex(),
                    block.getResearch(),
                    block.getPublicationTitle(),
                    block.getPublicationAuthors(),
                    block.getJournal(),
                    block.getVolume(),
                    block.getNumber(),
                    block.getPages(),
                    block.getYear(),
                    block.getPublisher(),
                    block.getOrganization(),
                    block.getBooktitle(),
                    block.getProfessionalActivity(),
                    block.getFacultyParticipation(),
                    projects,
                    block.getHash()
            );
        }

        boolean isMentorReview() {
            return "MAR_RECORD".equals(name) && projects != null && projects.contains("\"MENTOR_REVIEW\"");
        }

        Block toBlock(String previousHash) {
            return toBlock(previousHash, projects);
        }

        Block toBlock(String previousHash, String overrideProjects) {
            Block rebuilt = new Block(
                    name, email, department, dateOfJoining, dateOfAssign,
                    specialization, teachingExp, qualifications,
                    publications, rawBibtex, research,
                    publicationTitle, publicationAuthors, journal,
                    volume, number, pages, year,
                    publisher, organization, booktitle,
                    professionalActivity, facultyParticipation, overrideProjects,
                    previousHash
            );
            rebuilt.mineBlock(3);
            return rebuilt;
        }
    }

    public static class RolloverResult {
        public final boolean success;
        public final String semTag;
        public final String message;

        private RolloverResult(boolean success, String semTag, String message) {
            this.success = success;
            this.semTag = semTag;
            this.message = message;
        }

        static RolloverResult success(String semTag, String message) {
            return new RolloverResult(true, semTag, message);
        }

        static RolloverResult failure(String message) {
            return new RolloverResult(false, null, message);
        }
    }
}
