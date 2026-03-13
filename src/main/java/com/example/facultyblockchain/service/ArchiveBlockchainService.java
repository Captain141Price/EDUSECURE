package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.model.StudentExcelModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class ArchiveBlockchainService {

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

    @Autowired
    private SubjectCatalogService subjectCatalogService;

    public Map<String, Map<String, Object>> getStudentTimeline(String universityRoll) {
        Map<String, Map<String, Object>> timeline = new LinkedHashMap<>();

        StudentExcelModel activeStudent = findActiveStudent(universityRoll);
        if (activeStudent != null) {
            timeline.put("active", buildSection("Active Records", activeStudent,
                    blockchainService.getMarksBlocks(),
                    blockchainService.getAttendanceBlocks(),
                    blockchainService.getMarBlocks(),
                    blockchainService.getMoocsBlocks()));
        }

        StudentExcelModel archivedStudent = findArchivedStudent(universityRoll);
        if (archivedStudent != null) {
            timeline.put("archived", buildSection("Archived Alumni Records", archivedStudent,
                    loadBlocks(ARCHIVE_MARKS_FILE),
                    loadBlocks(ARCHIVE_ATTENDANCE_FILE),
                    loadBlocks(ARCHIVE_MAR_FILE),
                    loadBlocks(ARCHIVE_MOOCS_FILE)));
        }

        return timeline;
    }

    public Map<String, String> findStudentIdentity(String universityRoll) {
        StudentExcelModel active = findActiveStudent(universityRoll);
        if (active != null) {
            return toIdentity(active, "ACTIVE");
        }

        StudentExcelModel archived = findArchivedStudent(universityRoll);
        if (archived != null) {
            return toIdentity(archived, "ARCHIVED");
        }

        return null;
    }

    public List<String> listArchiveFiles() {
        List<String> files = new ArrayList<>();
        for (String file : List.of(
                ARCHIVE_STUDENTS_FILE,
                ARCHIVE_MARKS_FILE,
                ARCHIVE_ATTENDANCE_FILE,
                ARCHIVE_MAR_FILE,
                ARCHIVE_MOOCS_FILE)) {
            File candidate = new File(file);
            if (candidate.exists()) {
                files.add(candidate.getName());
            }
        }
        return files;
    }

    public List<String> listArchivedSemesters() {
        List<StudentExcelModel> archivedStudents = loadStudents(ARCHIVE_STUDENTS_FILE);
        Set<String> sessions = new TreeSet<>();
        for (StudentExcelModel student : archivedStudents) {
            if (student.getPassingYear() != null && !student.getPassingYear().isBlank()) {
                sessions.add(student.getPassingYear().trim());
            }
        }
        return new ArrayList<>(sessions);
    }

    private Map<String, String> toIdentity(StudentExcelModel student, String source) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", student.getName());
        info.put("email", student.getEmail());
        info.put("department", student.getDepartment());
        info.put("universityRoll", student.getRoll());
        info.put("passingYear", student.getPassingYear());
        info.put("semester", student.getSemester());
        info.put("source", source);
        return info;
    }

    private Map<String, Object> buildSection(
            String label,
            StudentExcelModel student,
            List<Block> marksBlocks,
            List<Block> attendanceBlocks,
            List<Block> marBlocks,
            List<Block> moocsBlocks) {

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("label", label);
        section.put("studentName", student.getName());
        section.put("email", student.getEmail());
        section.put("department", student.getDepartment());
        section.put("universityRoll", student.getRoll());
        section.put("passingYear", student.getPassingYear());
        section.put("semester", student.getSemester());
        section.put("marks", extractMarks(marksBlocks, student));
        section.put("attendance", extractAttendance(attendanceBlocks, student));
        section.put("mar", extractMar(marBlocks, student));
        section.put("moocs", extractMoocs(moocsBlocks, student));
        return section;
    }

    private List<Map<String, Object>> extractMarks(List<Block> chain, StudentExcelModel student) {
        List<Map<String, Object>> result = new ArrayList<>();
        String email = normalize(student.getEmail());

        for (Block block : chain) {
            if (!"MARKS_RECORD".equals(block.getName()) || block.getProjects() == null || block.getProjects().isEmpty()) {
                continue;
            }

            try {
                Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
                List<Map<String, Object>> students = castRows(payload.get("students"));
                Map<String, Object> meta = castMap(payload.get("meta"));
                List<String> examLabels = castStringList(payload.get("examLabels"));

                for (Map<String, Object> row : students) {
                    if (!email.equals(normalize(row.get("email")))) {
                        continue;
                    }

                    String subjectCode = firstNonBlank(
                            readString(meta.get("courseCode")),
                            readString(meta.get("subjectName")));
                    String semester = firstNonBlank(readString(meta.get("year")), student.getSemester());
                    String department = firstNonBlank(readString(meta.get("departmentName")), student.getDepartment());

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("subjectName", resolveSubjectName(department, semester, subjectCode));
                    entry.put("subjectCode", subjectCode);
                    entry.put("courseCode", subjectCode);
                    entry.put("examLabels", examLabels);
                    entry.put("marks", row.get("marks"));
                    entry.put("teacherEmail", block.getEmail());
                    result.add(entry);
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private List<Map<String, Object>> extractAttendance(List<Block> chain, StudentExcelModel student) {
        List<Map<String, Object>> result = new ArrayList<>();
        String email = normalize(student.getEmail());

        for (Block block : chain) {
            if (!"ATTENDANCE_RECORD".equals(block.getName()) || block.getProjects() == null || block.getProjects().isEmpty()) {
                continue;
            }

            try {
                Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
                List<Map<String, Object>> students = castRows(payload.get("students"));
                Map<String, Object> meta = castMap(payload.get("meta"));

                for (Map<String, Object> row : students) {
                    if (!email.equals(normalize(row.get("email")))) {
                        continue;
                    }

                    int present = 0;
                    int total = 0;
                    Object attendanceList = row.get("attendance");
                    if (attendanceList instanceof List<?> values) {
                        for (Object value : values) {
                            if (value instanceof Boolean boolValue) {
                                total++;
                                if (boolValue) {
                                    present++;
                                }
                            }
                        }
                    }

                    String subjectCode = firstNonBlank(
                            readString(meta.get("courseCode")),
                            readString(meta.get("subjectName")),
                            readString(payload.get("courseId")));
                    String semester = firstNonBlank(readString(meta.get("year")), student.getSemester());
                    String department = firstNonBlank(readString(meta.get("departmentName")), student.getDepartment());
                    double percentage = total == 0 ? 0 : Math.round((present * 1000.0 / total)) / 10.0;

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("subjectName", resolveSubjectName(department, semester, subjectCode));
                    entry.put("courseCode", subjectCode);
                    entry.put("present", present);
                    entry.put("total", total);
                    entry.put("percentage", percentage);
                    result.add(entry);
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private List<Map<String, Object>> extractMar(List<Block> chain, StudentExcelModel student) {
        List<Map<String, Object>> result = new ArrayList<>();
        String email = normalize(student.getEmail());
        Map<String, Map<String, String>> reviews = new HashMap<>();

        for (Block block : chain) {
            if (block.getProjects() == null || block.getProjects().isEmpty()) {
                continue;
            }

            try {
                Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
                if ("MENTOR_REVIEW".equals(payload.get("action"))) {
                    Map<String, String> review = new HashMap<>();
                    review.put("status", readString(payload.get("status")));
                    review.put("mentor", readString(payload.get("mentorEmail")));
                    reviews.put(readString(payload.get("originalHash")), review);
                }
            } catch (Exception ignored) {
            }
        }

        for (Block block : chain) {
            if (!"MAR_RECORD".equals(block.getName()) || block.getProjects() == null || block.getProjects().isEmpty()) {
                continue;
            }

            if (!email.equals(normalize(block.getEmail()))) {
                continue;
            }

            try {
                Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
                if ("MENTOR_REVIEW".equals(payload.get("action"))) {
                    continue;
                }

                Map<String, String> review = reviews.getOrDefault(block.getHash(), Map.of());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("activityName", firstNonBlank(readString(payload.get("activityName")), readString(payload.get("name"))));
                entry.put("category", firstNonBlank(readString(payload.get("category")), readString(payload.get("type"))));
                entry.put("approvalStatus", firstNonBlank(review.get("status"), "Pending"));
                entry.put("mentor", firstNonBlank(review.get("mentor"), "Not Assigned"));
                entry.put("points", readString(payload.get("points")));
                entry.put("date", firstNonBlank(readString(payload.get("activityDate")), readString(payload.get("date"))));
                result.add(entry);
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private List<Map<String, Object>> extractMoocs(List<Block> chain, StudentExcelModel student) {
        List<Map<String, Object>> result = new ArrayList<>();
        String email = normalize(student.getEmail());

        for (Block block : chain) {
            if (!"MOOCS_RECORD".equals(block.getName()) || block.getProjects() == null || block.getProjects().isEmpty()) {
                continue;
            }

            if (!email.equals(normalize(block.getEmail()))) {
                continue;
            }

            try {
                Map<String, Object> payload = objectMapper.readValue(block.getProjects(), new TypeReference<>() {});
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("courseName", firstNonBlank(readString(payload.get("courseName")), readString(payload.get("name"))));
                entry.put("platform", readString(payload.get("platform")));
                entry.put("completionDate", firstNonBlank(readString(payload.get("completionDate")), readString(payload.get("date"))));
                entry.put("certificate", firstNonBlank(readString(payload.get("certificate")), readString(payload.get("filePath")), readString(payload.get("filepath"))));
                entry.put("credits", readString(payload.get("credits")));
                result.add(entry);
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private StudentExcelModel findActiveStudent(String universityRoll) {
        return studentExcelService.getAllStudents().stream()
                .filter(student -> universityRoll.equalsIgnoreCase(readString(student.getRoll())))
                .findFirst()
                .orElse(null);
    }

    private StudentExcelModel findArchivedStudent(String universityRoll) {
        return loadStudents(ARCHIVE_STUDENTS_FILE).stream()
                .filter(student -> universityRoll.equalsIgnoreCase(readString(student.getRoll())))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Block> loadBlocks(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return List.of();
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (List<Block>) in.readObject();
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<StudentExcelModel> loadStudents(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return List.of();
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (List<StudentExcelModel>) in.readObject();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> castRows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            rows.add(castMap(item));
        }
        return rows;
    }

    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (Object item : list) {
            items.add(readString(item));
        }
        return items;
    }

    private String resolveSubjectName(String department, String semester, String subjectCode) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return "Unknown Subject";
        }

        return subjectCatalogService.findByCourseCode(department, semester, subjectCode)
                .map(subject -> subject.getCourseName())
                .orElse(subjectCode);
    }

    private String normalize(Object value) {
        return readString(value).trim().toLowerCase();
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
