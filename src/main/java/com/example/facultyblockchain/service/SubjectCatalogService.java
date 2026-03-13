package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.SubjectEntry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * SubjectCatalogService
 *
 * Loads subjects.xlsx from the application working directory into memory at
 * startup. Subsequent reads are served from the in-memory cache — no disk I/O
 * on every request.
 *
 * subjects.xlsx expected column order (row 0 = header, ignored):
 *   Col 0: Department   (e.g. CSE, IT, AIML, ECE, EE)
 *   Col 1: Semester     (numeric 1-8)
 *   Col 2: CourseName
 *   Col 3: CourseCode
 *   Col 4: SubjectType  (THEORY / PRACTICAL / SESSIONAL)
 *
 * Department mapping:
 *   The student system uses CSE1 / CSE2 / CSE3 for CSE sections.
 *   The subjects file uses the canonical "CSE".
 *   This service normalises CSE1/CSE2/CSE3 → CSE for lookup so the
 *   same subject list is served for all three CSE sections.
 *
 * Elective flag:
 *   A subject is marked elective when semester >= 5 AND the course code
 *   contains the prefix PEC or OEC (case-insensitive). These subjects require
 *   manual student assignment by the admin instead of auto-enrollment.
 *
 * Cache reload:
 *   Call reloadCache() at runtime (e.g. after the admin uploads a new
 *   subjects.xlsx) to refresh without restarting the server.
 */
@Service
public class SubjectCatalogService {

    private static final String SUBJECTS_FILE_PATH =
            System.getProperty("user.dir") + "/subjects.xlsx";

    /** Thread-safe in-memory cache */
    private final List<SubjectEntry> cache = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // -----------------------------------------------------------------------
    // Startup
    // -----------------------------------------------------------------------

    @PostConstruct
    public void init() {
        reloadCache();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns subjects filtered by department and semester.
     *
     * @param department  Raw department value from the Admin UI
     *                    (e.g. "CSE1", "CSE2", "CSE3", "IT", "AIML", "ECE", "EE")
     * @param semesterStr Semester number as String (e.g. "5")
     * @return Ordered list of matching SubjectEntry objects
     */
    public List<SubjectEntry> getSubjects(String department, String semesterStr) {
        if (department == null || department.isBlank() ||
            semesterStr  == null || semesterStr.isBlank()) {
            return Collections.emptyList();
        }

        int semester;
        try {
            semester = Integer.parseInt(semesterStr.trim());
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }

        // Normalise department: CSE1/CSE2/CSE3 → CSE
        String normDept = normaliseDepartment(department.trim());

        lock.readLock().lock();
        try {
            return cache.stream()
                    .filter(s -> s.getDepartment().equalsIgnoreCase(normDept)
                              && s.getSemester() == semester)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Looks up a single subject by department, semester, and course code.
     * Used to validate the auto-bound course code on the server side.
     */
    public Optional<SubjectEntry> findByCourseCode(String department,
                                                    String semesterStr,
                                                    String courseCode) {
        return getSubjects(department, semesterStr).stream()
                .filter(s -> s.getCourseCode().equalsIgnoreCase(courseCode))
                .findFirst();
    }

    /**
     * Returns distinct list of departments present in the subjects file.
     * Does NOT include the CSE1/CSE2/CSE3 aliases — callers normalise
     * themselves when needed.
     */
    public List<String> getDepartments() {
        lock.readLock().lock();
        try {
            return cache.stream()
                    .map(SubjectEntry::getDepartment)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns cached entry count — useful for health-check logging.
     */
    public int getCacheSize() {
        lock.readLock().lock();
        try { return cache.size(); }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Re-reads subjects.xlsx from disk and rebuilds the in-memory cache.
     * Thread-safe: acquires write lock during the swap.
     */
    public void reloadCache() {
        List<SubjectEntry> freshEntries = loadFromDisk();

        lock.writeLock().lock();
        try {
            cache.clear();
            cache.addAll(freshEntries);
        } finally {
            lock.writeLock().unlock();
        }

        System.out.printf("[SubjectCatalogService] Cache loaded: %d subjects from %s%n",
                freshEntries.size(), SUBJECTS_FILE_PATH);
    }

    // -----------------------------------------------------------------------
    // Static helper: is a subject an elective?
    // -----------------------------------------------------------------------

    /**
     * A subject is considered elective if semester >= 5 AND the course code
     * starts with "PEC" or "OEC" (Professional / Open Elective Course).
     * Electives require manual student selection by the admin.
     */
    public static boolean isElective(SubjectEntry subject) {
        if (subject.getSemester() < 5) return false;
        String code = subject.getCourseCode().toUpperCase();
        return code.startsWith("PEC") || code.startsWith("OEC");
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<SubjectEntry> loadFromDisk() {
        List<SubjectEntry> entries = new ArrayList<>();
        File file = new File(SUBJECTS_FILE_PATH);

        if (!file.exists()) {
            System.err.printf("[SubjectCatalogService] WARNING: subjects.xlsx not found at %s%n",
                    SUBJECTS_FILE_PATH);
            return entries;
        }

        DataFormatter fmt = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook    = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Row 0 is the header — skip it
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String department  = fmt.formatCellValue(row.getCell(0)).trim();
                String semesterStr = fmt.formatCellValue(row.getCell(1)).trim();
                String courseName  = fmt.formatCellValue(row.getCell(2)).trim();
                String courseCode  = fmt.formatCellValue(row.getCell(3)).trim();
                String subjectType = fmt.formatCellValue(row.getCell(4)).trim();

                // Skip blank or incomplete rows
                if (department.isEmpty() || semesterStr.isEmpty() ||
                    courseName.isEmpty()  || courseCode.isEmpty()) {
                    continue;
                }

                // Skip the placeholder "-" course code (ECE sem 8 Design Lab)
                if ("-".equals(courseCode)) continue;

                int semester;
                try {
                    // Semester may be stored as numeric or text
                    semester = (int) Double.parseDouble(semesterStr);
                } catch (NumberFormatException e) {
                    System.err.printf("[SubjectCatalogService] Row %d: invalid semester '%s' — skipped%n",
                            i, semesterStr);
                    continue;
                }

                entries.add(new SubjectEntry(department, semester, courseName,
                                             courseCode, subjectType));
            }

        } catch (IOException e) {
            System.err.printf("[SubjectCatalogService] ERROR reading subjects.xlsx: %s%n",
                    e.getMessage());
        }

        return entries;
    }

    /**
     * Normalises student-system department codes to the canonical codes used
     * in subjects.xlsx.
     *   CSE1, CSE2, CSE3  →  CSE
     *   Everything else   →  as-is (IT, AIML, ECE, EE)
     */
    private static String normaliseDepartment(String dept) {
        if (dept == null) return "";
        String upper = dept.toUpperCase();
        if (upper.startsWith("CSE")) return "CSE";
        return upper;
    }
}
