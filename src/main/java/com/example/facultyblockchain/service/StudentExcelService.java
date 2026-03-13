package com.example.facultyblockchain.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import com.example.facultyblockchain.model.StudentExcelModel;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Service
public class StudentExcelService {

    /** Default password assigned to all system-created/imported students. Change here to update everywhere. */
    public static final String DEFAULT_STUDENT_PASSWORD = "stcet@123";

    private static final String FILE_PATH = System.getProperty("user.dir") + "/StudentData.xlsx";
    private static final String ACTIVE_STUDENTS_FILE_PATH = System.getProperty("user.dir") + "/active_students.dat";

    public synchronized void saveStudent(
            String name,
            String email,
            String roll,
            String semester,
            String passingYear,
            String department) {
        try {
            Workbook workbook;
            Sheet sheet;

            File file = new File(FILE_PATH);
            if (file.exists()) {
                workbook = new XSSFWorkbook(new FileInputStream(file));
                sheet = workbook.getSheetAt(0);
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("Students");

                // Header row (Updated schema)
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Roll No"); // Assuming original cell 0 was classRoll based on
                                                              // getAllStudents parser
                header.createCell(1).setCellValue("Email");
                header.createCell(2).setCellValue("Name");
                header.createCell(3).setCellValue("University Roll");
                header.createCell(4).setCellValue("Department");
                header.createCell(5).setCellValue("Passing Year");
                header.createCell(6).setCellValue("Semester");
            }

            int rowCount = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowCount);

            row.createCell(0).setCellValue(String.valueOf(rowCount)); // Auto-gen class roll
            row.createCell(1).setCellValue(email);
            row.createCell(2).setCellValue(name);
            row.createCell(3).setCellValue(roll);
            row.createCell(4).setCellValue(department);
            row.createCell(5).setCellValue(passingYear);
            row.createCell(6).setCellValue(semester);

            FileOutputStream fos = new FileOutputStream(FILE_PATH);
            workbook.write(fos);
            fos.close();
            workbook.close();
            syncActiveStudentsDat();

            System.out.println("Student saved to Excel");

        } catch (Exception e) {
            throw new RuntimeException("Failed to write Excel", e);
        }
    }

    /**
     * Saves a student with an EXPLICIT classRoll (used by manual Add Student form).
     * The classRoll is provided by the admin, not auto-generated.
     */
    public synchronized void saveStudentWithClassRoll(
            String classRoll,
            String name,
            String email,
            String roll,
            String semester,
            String passingYear,
            String department) {
        try {
            Workbook workbook;
            Sheet sheet;

            File file = new File(FILE_PATH);
            if (file.exists()) {
                workbook = new XSSFWorkbook(new FileInputStream(file));
                sheet = workbook.getSheetAt(0);
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("Students");

                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Roll No");
                header.createCell(1).setCellValue("Email");
                header.createCell(2).setCellValue("Name");
                header.createCell(3).setCellValue("University Roll");
                header.createCell(4).setCellValue("Department");
                header.createCell(5).setCellValue("Passing Year");
                header.createCell(6).setCellValue("Semester");
            }

            int rowCount = sheet.getLastRowNum() + 1;
            Row row = sheet.createRow(rowCount);

            row.createCell(0).setCellValue(classRoll);  // Use provided classRoll
            row.createCell(1).setCellValue(email);
            row.createCell(2).setCellValue(name);
            row.createCell(3).setCellValue(roll);
            row.createCell(4).setCellValue(department);
            row.createCell(5).setCellValue(passingYear);
            row.createCell(6).setCellValue(semester);

            FileOutputStream fos = new FileOutputStream(FILE_PATH);
            workbook.write(fos);
            fos.close();
            workbook.close();
            syncActiveStudentsDat();

            System.out.println("Student saved to Excel with classRoll=" + classRoll);

        } catch (Exception e) {
            throw new RuntimeException("Failed to write Excel", e);
        }
    }


    @org.springframework.beans.factory.annotation.Autowired
    private LoginBlockchainService loginBlockchainService;

    @PostConstruct
    public void initializeActiveStudentsSnapshot() {
        File file = new File(FILE_PATH);
        if (file.exists()) {
            syncActiveStudentsDat();
        }
    }

    public int processExcelUpload(org.springframework.web.multipart.MultipartFile file) throws Exception {
        int addedCount = 0;
        int skippedCount = 0;
        DataFormatter df = new DataFormatter();

        // Allowed departments (same whitelist as StudentController)
        java.util.Set<String> ALLOWED_DEPTS = java.util.Set.of(
                "CSE1", "CSE2", "CSE3", "AIML", "IT", "ECE", "EE");

        try (InputStream is = file.getInputStream();
                Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // -------------------------------------------------------
            // Expected upload Excel column order (7 columns):
            //   Col 0: ClassRoll
            //   Col 1: Name
            //   Col 2: Department
            //   Col 3: PassingYear
            //   Col 4: Semester
            //   Col 5: Email
            //   Col 6: University Roll
            // -------------------------------------------------------
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String classRoll  = df.formatCellValue(row.getCell(0)).trim();
                    String name       = df.formatCellValue(row.getCell(1)).trim();
                    String department = df.formatCellValue(row.getCell(2)).trim();
                    String passingYear= df.formatCellValue(row.getCell(3)).trim();
                    String semester   = df.formatCellValue(row.getCell(4)).trim();
                    String email      = df.formatCellValue(row.getCell(5)).trim();
                    String universityRoll = df.formatCellValue(row.getCell(6)).trim();

                    // Skip blank rows
                    if (name.isEmpty() || email.isEmpty()) {
                        System.out.println("[ExcelUpload] Row " + i + " skipped: name or email is empty");
                        skippedCount++;
                        continue;
                    }

                    // Validate classRoll is numeric
                    if (classRoll.isEmpty() || !classRoll.matches("\\d+")) {
                        System.err.println("[ExcelUpload] Row " + i + " skipped: invalid ClassRoll='" + classRoll + "'");
                        skippedCount++;
                        continue;
                    }

                    // Validate department against whitelist
                    if (!ALLOWED_DEPTS.contains(department.toUpperCase())) {
                        System.err.println("[ExcelUpload] Row " + i + " skipped: invalid department='" + department
                                + "'. Allowed: " + ALLOWED_DEPTS);
                        skippedCount++;
                        continue;
                    }

                    // Normalise department to upper-case to match whitelist
                    department = department.toUpperCase();

                    // Skip if student already exists
                    if (emailExists(email)) {
                        System.out.println("[ExcelUpload] Row " + i + " skipped: email already exists: " + email);
                        skippedCount++;
                        continue;
                    }

                    // Save to Excel with explicit classRoll and University Roll
                    saveStudentWithClassRoll(classRoll, name, email, universityRoll, semester, passingYear, department);

                    // Add login credentials to blockchain
                    loginBlockchainService.addUser(name, email, DEFAULT_STUDENT_PASSWORD, "student");

                    addedCount++;
                    System.out.println("[ExcelUpload] Row " + i + " imported: " + email);

                } catch (Exception e) {
                    System.err.println("[ExcelUpload] Row " + i + " error: " + e.getMessage());
                    skippedCount++;
                }
            }
        }
        System.out.println("[ExcelUpload] Done — added=" + addedCount + ", skipped=" + skippedCount);
        return addedCount;
    }


    public List<StudentExcelModel> getAllStudents() {
        List<StudentExcelModel> students = new ArrayList<>();
        DataFormatter df = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(FILE_PATH);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                try {
                    String classRoll = df.formatCellValue(row.getCell(0));
                    String email = df.formatCellValue(row.getCell(1));
                    String name = df.formatCellValue(row.getCell(2));
                    String univRoll = df.formatCellValue(row.getCell(3));
                    String dept = df.formatCellValue(row.getCell(4));
                    String passingYear = df.formatCellValue(row.getCell(5));
                    String semester = df.formatCellValue(row.getCell(6));

                    // Excel stored schema: col0=classRoll, col1=email, col2=name, col3=univRoll, col4=dept, col5=passingYear, col6=semester
                    // Constructor: StudentExcelModel(name, email, classRoll, roll, semester, passingYear, dept)
                    StudentExcelModel s = new StudentExcelModel(
                            name, email, classRoll, univRoll, semester, passingYear, dept);
                    students.add(s);
                } catch (Exception e) {
                    System.err.println("Skipping row " + i + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Excel parsing error: " + e.getMessage());
        }

        return students;
    }

    public boolean emailExists(String email) {
        File file = new File(FILE_PATH);
        if (!file.exists())
            return false;

        // FIX: Use try-with-resources to guarantee workbook is always closed (prevents file handle leak)
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Cell cell = row.getCell(1); // Email column (col 1 in stored schema)
                    if (cell != null && email.equalsIgnoreCase(cell.getStringCellValue())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<StudentExcelModel> findByEmail(String email) {
        return getAllStudents().stream()
                .filter(s -> s.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    public synchronized void promoteAllStudents() {
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                throw new RuntimeException("Student Excel file not found");
            }

            Workbook workbook = new XSSFWorkbook(new FileInputStream(file));
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                Cell semesterCell = row.getCell(6);
                if (semesterCell == null)
                    continue;

                String currentSemStr = semesterCell.getStringCellValue();
                try {
                    int sem = Integer.parseInt(currentSemStr);
                    if (sem < 8) {
                        semesterCell.setCellValue(String.valueOf(sem + 1));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid semester format for row " + i + ": " + currentSemStr);
                }
            }

            FileOutputStream fos = new FileOutputStream(FILE_PATH);
            workbook.write(fos);
            fos.close();
            workbook.close();
            syncActiveStudentsDat();

            System.out.println("Global student promotion completed");

        } catch (Exception e) {
            throw new RuntimeException("Failed to promote students in Excel", e);
        }
    }

    public synchronized void updateStudentByEmail(
            String email,
            String name,
            String classRoll,
            String roll,
            String semester,
            String passingYear,
            String department) {
        try (FileInputStream fis = new FileInputStream(FILE_PATH);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                if (email.equalsIgnoreCase(row.getCell(1).getStringCellValue())) {
                    if (classRoll != null && !classRoll.isEmpty())
                        row.getCell(0).setCellValue(classRoll);
                    if (name != null)
                        row.getCell(2).setCellValue(name);
                    row.getCell(3).setCellValue(roll);
                    row.getCell(4).setCellValue(department);
                    row.getCell(5).setCellValue(passingYear);
                    row.getCell(6).setCellValue(semester);
                    break;
                }
            }

            FileOutputStream fos = new FileOutputStream(FILE_PATH);
            workbook.write(fos);
            fos.close();
            syncActiveStudentsDat();

        } catch (Exception e) {
            throw new RuntimeException("Failed to update student", e);
        }
    }

    public Map<String, List<String>> getFilterOptions() {
        // Departments and semesters are HARDCODED to the validated allowlist.
        // This prevents legacy/mis-mapped Excel data from polluting these dropdowns.
        List<String> departments = java.util.Arrays.asList(
                "CSE1", "CSE2", "CSE3", "AIML", "IT", "ECE", "EE");

        List<String> semesters = java.util.Arrays.asList(
                "1", "2", "3", "4", "5", "6", "7", "8");

        // Passing years remain dynamic — derive from actual student records
        List<String> passingYears = new ArrayList<>();
        for (StudentExcelModel student : getAllStudents()) {
            if (student.getPassingYear() != null && !student.getPassingYear().trim().isEmpty()
                    && !passingYears.contains(student.getPassingYear())) {
                passingYears.add(student.getPassingYear());
            }
        }
        passingYears.sort(null); // sort years ascending

        Map<String, List<String>> options = new HashMap<>();
        options.put("departments", departments);
        options.put("passingYears", passingYears);
        options.put("semesters", semesters);
        return options;
    }

    public List<StudentExcelModel> filterStudents(String department, String passingYear, String semester) {
        List<StudentExcelModel> filtered = new ArrayList<>();
        for (StudentExcelModel student : getAllStudents()) {
            boolean matchDept = department == null || department.isEmpty()
                    || department.equalsIgnoreCase(student.getDepartment());
            boolean matchYear = passingYear == null || passingYear.isEmpty()
                    || passingYear.equalsIgnoreCase(student.getPassingYear());
            boolean matchSem = semester == null || semester.isEmpty()
                    || semester.equalsIgnoreCase(student.getSemester());

            if (matchDept && matchYear && matchSem) {
                filtered.add(student);
            }
        }
        return filtered;
    }

    public synchronized List<StudentExcelModel> removeStudentsForArchive(String passingYear, String semester) {
        List<StudentExcelModel> removedStudents = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Integer> rowsToRemove = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String rowPassingYear = formatter.formatCellValue(row.getCell(5)).trim();
                String rowSemester = formatter.formatCellValue(row.getCell(6)).trim();

                if (!passingYear.equals(rowPassingYear) || !semester.equals(rowSemester)) {
                    continue;
                }

                removedStudents.add(new StudentExcelModel(
                        formatter.formatCellValue(row.getCell(2)).trim(),
                        formatter.formatCellValue(row.getCell(1)).trim(),
                        formatter.formatCellValue(row.getCell(0)).trim(),
                        formatter.formatCellValue(row.getCell(3)).trim(),
                        rowSemester,
                        rowPassingYear,
                        formatter.formatCellValue(row.getCell(4)).trim()
                ));
                rowsToRemove.add(i);
            }

            for (int i = rowsToRemove.size() - 1; i >= 0; i--) {
                int rowIndex = rowsToRemove.get(i);
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    sheet.removeRow(row);
                }
                if (rowIndex < sheet.getLastRowNum()) {
                    sheet.shiftRows(rowIndex + 1, sheet.getLastRowNum(), -1);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(FILE_PATH)) {
                workbook.write(fos);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove archived students from active registry", e);
        }

        syncActiveStudentsDat();
        return removedStudents;
    }

    private synchronized void syncActiveStudentsDat() {
        saveActiveStudentsSnapshot(getAllStudents());
    }

    private void saveActiveStudentsSnapshot(List<StudentExcelModel> students) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(ACTIVE_STUDENTS_FILE_PATH))) {
            out.writeObject(new ArrayList<>(students));
        } catch (IOException e) {
            throw new RuntimeException("Failed to sync active_students.dat", e);
        }
    }
}
