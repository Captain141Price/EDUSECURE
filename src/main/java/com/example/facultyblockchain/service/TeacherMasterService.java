package com.example.facultyblockchain.service;

import com.example.facultyblockchain.model.Block;
import com.example.facultyblockchain.model.LoginBlock;
import com.example.facultyblockchain.model.TeacherMasterRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class TeacherMasterService {

    private static final String BASE = System.getProperty("user.dir") + "/";
    private static final String MASTER_FILE = BASE + "teachers_master.dat";
    private static final String TEACHERS_EXCEL_FILE = BASE + "teachers.xlsx";
    private static final String FACULTY_EXPORT_FILE = BASE + "faculty_profiles.xlsx";

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private LoginBlockchainService loginBlockchainService;

    @PostConstruct
    public void init() {
        if (!new File(MASTER_FILE).exists()) {
            bootstrapMasterData();
        } else {
            ensureKnownTeachersPresent();
        }
    }

    public synchronized List<TeacherMasterRecord> getAllTeachers() {
        return new ArrayList<>(loadMasterData().values());
    }

    public synchronized Optional<TeacherMasterRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return loadMasterData().values().stream()
                .filter(record -> email.equalsIgnoreCase(record.getEmail()))
                .findFirst()
                .map(this::copyOf);
    }

    public synchronized Optional<TeacherMasterRecord> findByTeacherId(String teacherId) {
        if (teacherId == null || teacherId.isBlank()) {
            return Optional.empty();
        }
        return loadMasterData().values().stream()
                .filter(record -> teacherId.equalsIgnoreCase(record.getTeacherId()))
                .findFirst()
                .map(this::copyOf);
    }

    public synchronized TeacherMasterRecord saveTeacher(TeacherMasterRecord incoming) {
        LinkedHashMap<String, TeacherMasterRecord> data = loadMasterData();
        TeacherMasterRecord target = findExistingRecord(data, incoming);
        if (target == null) {
            target = new TeacherMasterRecord();
        }

        merge(target, incoming);
        if (target.getStatus() == null || target.getStatus().isBlank()) {
            target.setStatus("Active");
        }

        data.put(buildStorageKey(target), target);
        saveMasterData(data.values());
        ensureTeacherLoginAccount(target);
        return copyOf(target);
    }

    public synchronized TeacherMasterRecord syncFromBlock(Block block, String teacherId, String status) {
        TeacherMasterRecord record = new TeacherMasterRecord();
        record.setTeacherId(teacherId);
        record.setName(block.getName());
        record.setEmail(block.getEmail());
        record.setDepartment(block.getDepartment());
        record.setStatus(status);
        record.setSpecialization(block.getSpecialization());
        record.setTeachingExperience(block.getTeachingExp());
        record.setQualifications(block.getQualifications());
        record.setDateOfJoining(block.getDateOfJoining());
        record.setDateOfAssign(block.getDateOfAssign());
        record.setResearchDetails(block.getResearch());
        record.setProfessionalActivities(block.getProfessionalActivity());
        record.setParticipation(block.getFacultyParticipation());
        record.setProjects(block.getProjects());
        record.setPublications(block.getPublications());
        record.setRawBibtex(block.getRawBibtex());
        return saveTeacher(record);
    }

    public synchronized int importTeachersExcel(MultipartFile file) throws IOException {
        Files.copy(file.getInputStream(), Path.of(TEACHERS_EXCEL_FILE), StandardCopyOption.REPLACE_EXISTING);
        return importTeachersExcelFromDisk();
    }

    public synchronized int importTeachersExcelFromDisk() throws IOException {
        File file = new File(TEACHERS_EXCEL_FILE);
        if (!file.exists()) {
            return 0;
        }

        LinkedHashMap<String, TeacherMasterRecord> data = loadMasterData();
        int processed = 0;
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String teacherId = formatter.formatCellValue(row.getCell(0)).trim();
                String name = formatter.formatCellValue(row.getCell(1)).trim();
                String email = formatter.formatCellValue(row.getCell(2)).trim();
                String department = formatter.formatCellValue(row.getCell(3)).trim();
                String status = formatter.formatCellValue(row.getCell(4)).trim();

                if (teacherId.isEmpty() && email.isEmpty()) {
                    continue;
                }

                TeacherMasterRecord incoming = new TeacherMasterRecord();
                incoming.setTeacherId(teacherId);
                incoming.setName(name);
                incoming.setEmail(email);
                incoming.setDepartment(department);
                incoming.setStatus(status);

                TeacherMasterRecord target = findExistingRecord(data, incoming);
                if (target == null) {
                    target = new TeacherMasterRecord();
                }

                merge(target, incoming);
                if (target.getStatus() == null || target.getStatus().isBlank()) {
                    target.setStatus("Active");
                }
                data.put(buildStorageKey(target), target);
                processed++;
            }
        }

        saveMasterData(data.values());
        ensureTeacherLoginAccounts(data.values());
        return processed;
    }

    public synchronized File exportFacultyProfiles() throws IOException {
        List<TeacherMasterRecord> teachers = getAllTeachers();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Faculty Profiles");
            Row header = sheet.createRow(0);
            String[] columns = {
                    "TeacherID", "Name", "Email", "Department", "Status",
                    "Specialization", "Experience", "Qualifications", "DateOfJoining",
                    "DateOfAssign", "ResearchDetails", "ProfessionalActivities",
                    "Participation", "Projects", "Publications", "RawBibtex"
            };

            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int rowIndex = 1;
            for (TeacherMasterRecord teacher : teachers) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(value(teacher.getTeacherId()));
                row.createCell(1).setCellValue(value(teacher.getName()));
                row.createCell(2).setCellValue(value(teacher.getEmail()));
                row.createCell(3).setCellValue(value(teacher.getDepartment()));
                row.createCell(4).setCellValue(value(teacher.getStatus()));
                row.createCell(5).setCellValue(value(teacher.getSpecialization()));
                row.createCell(6).setCellValue(value(teacher.getTeachingExperience()));
                row.createCell(7).setCellValue(value(teacher.getQualifications()));
                row.createCell(8).setCellValue(value(teacher.getDateOfJoining()));
                row.createCell(9).setCellValue(value(teacher.getDateOfAssign()));
                row.createCell(10).setCellValue(value(teacher.getResearchDetails()));
                row.createCell(11).setCellValue(value(teacher.getProfessionalActivities()));
                row.createCell(12).setCellValue(value(teacher.getParticipation()));
                row.createCell(13).setCellValue(value(teacher.getProjects()));
                row.createCell(14).setCellValue(value(teacher.getPublications()));
                row.createCell(15).setCellValue(value(teacher.getRawBibtex()));
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            File exportFile = new File(FACULTY_EXPORT_FILE);
            try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                workbook.write(fos);
            }
            return exportFile;
        }
    }

    private synchronized void bootstrapMasterData() {
        LinkedHashMap<String, TeacherMasterRecord> data = new LinkedHashMap<>();

        File excelFile = new File(TEACHERS_EXCEL_FILE);
        if (excelFile.exists()) {
            try {
                importTeachersExcelFromDisk();
                data.putAll(loadMasterData());
            } catch (IOException ignored) {
            }
        }

        for (Block block : blockchainService.getFacultyBlocks()) {
            TeacherMasterRecord incoming = new TeacherMasterRecord();
            incoming.setName(block.getName());
            incoming.setEmail(block.getEmail());
            incoming.setDepartment(block.getDepartment());
            incoming.setSpecialization(block.getSpecialization());
            incoming.setTeachingExperience(block.getTeachingExp());
            incoming.setQualifications(block.getQualifications());
            incoming.setDateOfJoining(block.getDateOfJoining());
            incoming.setDateOfAssign(block.getDateOfAssign());
            incoming.setResearchDetails(block.getResearch());
            incoming.setProfessionalActivities(block.getProfessionalActivity());
            incoming.setParticipation(block.getFacultyParticipation());
            incoming.setProjects(block.getProjects());
            incoming.setPublications(block.getPublications());
            incoming.setRawBibtex(block.getRawBibtex());
            incoming.setStatus("Active");

            TeacherMasterRecord target = findExistingRecord(data, incoming);
            if (target == null) {
                target = new TeacherMasterRecord();
            }
            merge(target, incoming);
            data.put(buildStorageKey(target), target);
        }

        for (LoginBlock teacher : loginBlockchainService.loadBlockchain("teacher")) {
            TeacherMasterRecord incoming = new TeacherMasterRecord();
            incoming.setName(teacher.getName());
            incoming.setEmail(teacher.getEmail());
            incoming.setStatus("Active");

            TeacherMasterRecord target = findExistingRecord(data, incoming);
            if (target == null) {
                target = new TeacherMasterRecord();
            }
            merge(target, incoming);
            data.put(buildStorageKey(target), target);
        }

        saveMasterData(data.values());
        ensureTeacherLoginAccounts(data.values());
    }

    private synchronized void ensureKnownTeachersPresent() {
        LinkedHashMap<String, TeacherMasterRecord> data = loadMasterData();
        boolean changed = false;

        for (LoginBlock teacher : loginBlockchainService.loadBlockchain("teacher")) {
            TeacherMasterRecord incoming = new TeacherMasterRecord();
            incoming.setName(teacher.getName());
            incoming.setEmail(teacher.getEmail());
            incoming.setStatus("Active");

            TeacherMasterRecord target = findExistingRecord(data, incoming);
            if (target == null) {
                target = new TeacherMasterRecord();
                merge(target, incoming);
                data.put(buildStorageKey(target), target);
                changed = true;
            }
        }

        if (changed) {
            saveMasterData(data.values());
        }
        ensureTeacherLoginAccounts(data.values());
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, TeacherMasterRecord> loadMasterData() {
        File file = new File(MASTER_FILE);
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            List<TeacherMasterRecord> stored = (List<TeacherMasterRecord>) in.readObject();
            LinkedHashMap<String, TeacherMasterRecord> records = new LinkedHashMap<>();
            for (TeacherMasterRecord record : stored) {
                records.put(buildStorageKey(record), record);
            }
            return records;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void saveMasterData(Collection<TeacherMasterRecord> teachers) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(MASTER_FILE))) {
            out.writeObject(new ArrayList<>(teachers));
        } catch (IOException e) {
            throw new RuntimeException("Unable to save teachers_master.dat", e);
        }
    }

    private TeacherMasterRecord findExistingRecord(Map<String, TeacherMasterRecord> data, TeacherMasterRecord incoming) {
        if (incoming.getTeacherId() != null && !incoming.getTeacherId().isBlank()) {
            for (TeacherMasterRecord record : data.values()) {
                if (incoming.getTeacherId().equalsIgnoreCase(value(record.getTeacherId()))) {
                    return record;
                }
            }
        }

        if (incoming.getEmail() != null && !incoming.getEmail().isBlank()) {
            for (TeacherMasterRecord record : data.values()) {
                if (incoming.getEmail().equalsIgnoreCase(value(record.getEmail()))) {
                    return record;
                }
            }
        }

        return null;
    }

    private void merge(TeacherMasterRecord target, TeacherMasterRecord incoming) {
        target.setTeacherId(firstNonBlank(incoming.getTeacherId(), target.getTeacherId()));
        target.setName(firstNonBlank(incoming.getName(), target.getName()));
        target.setEmail(firstNonBlank(incoming.getEmail(), target.getEmail()));
        target.setDepartment(firstNonBlank(incoming.getDepartment(), target.getDepartment()));
        target.setStatus(firstNonBlank(incoming.getStatus(), target.getStatus()));
        target.setSpecialization(firstNonBlank(incoming.getSpecialization(), target.getSpecialization()));
        target.setTeachingExperience(firstNonBlank(incoming.getTeachingExperience(), target.getTeachingExperience()));
        target.setQualifications(firstNonBlank(incoming.getQualifications(), target.getQualifications()));
        target.setDateOfJoining(firstNonBlank(incoming.getDateOfJoining(), target.getDateOfJoining()));
        target.setDateOfAssign(firstNonBlank(incoming.getDateOfAssign(), target.getDateOfAssign()));
        target.setResearchDetails(firstNonBlank(incoming.getResearchDetails(), target.getResearchDetails()));
        target.setProfessionalActivities(firstNonBlank(incoming.getProfessionalActivities(), target.getProfessionalActivities()));
        target.setParticipation(firstNonBlank(incoming.getParticipation(), target.getParticipation()));
        target.setProjects(firstNonBlank(incoming.getProjects(), target.getProjects()));
        target.setPublications(firstNonBlank(incoming.getPublications(), target.getPublications()));
        target.setRawBibtex(firstNonBlank(incoming.getRawBibtex(), target.getRawBibtex()));
    }

    private TeacherMasterRecord copyOf(TeacherMasterRecord source) {
        TeacherMasterRecord copy = new TeacherMasterRecord();
        copy.setTeacherId(source.getTeacherId());
        copy.setName(source.getName());
        copy.setEmail(source.getEmail());
        copy.setDepartment(source.getDepartment());
        copy.setStatus(source.getStatus());
        copy.setSpecialization(source.getSpecialization());
        copy.setTeachingExperience(source.getTeachingExperience());
        copy.setQualifications(source.getQualifications());
        copy.setDateOfJoining(source.getDateOfJoining());
        copy.setDateOfAssign(source.getDateOfAssign());
        copy.setResearchDetails(source.getResearchDetails());
        copy.setProfessionalActivities(source.getProfessionalActivities());
        copy.setParticipation(source.getParticipation());
        copy.setProjects(source.getProjects());
        copy.setPublications(source.getPublications());
        copy.setRawBibtex(source.getRawBibtex());
        return copy;
    }

    private String buildStorageKey(TeacherMasterRecord teacher) {
        if (teacher.getTeacherId() != null && !teacher.getTeacherId().isBlank()) {
            return "ID:" + teacher.getTeacherId().trim().toLowerCase();
        }
        return "EMAIL:" + value(teacher.getEmail()).trim().toLowerCase();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred.trim() : value(fallback);
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    private void ensureTeacherLoginAccounts(Collection<TeacherMasterRecord> teachers) {
        for (TeacherMasterRecord teacher : teachers) {
            ensureTeacherLoginAccount(teacher);
        }
    }

    private void ensureTeacherLoginAccount(TeacherMasterRecord teacher) {
        String email = value(teacher.getEmail());
        if (email.isBlank()) {
            return;
        }

        String name = value(teacher.getName());
        if (name.isBlank()) {
            int atIndex = email.indexOf('@');
            name = atIndex > 0 ? email.substring(0, atIndex) : email;
        }

        loginBlockchainService.ensureUserIfAbsent(name, email, LoginBlockchainService.DEFAULT_TEACHER_PASSWORD, "teacher");
    }
}
