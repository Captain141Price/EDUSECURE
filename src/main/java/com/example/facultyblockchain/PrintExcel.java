package com.example.facultyblockchain;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;

public class PrintExcel {
    public static void main(String[] args) throws Exception {
        File file = new File(System.getProperty("user.dir") + "/StudentData.xlsx");
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter df = new DataFormatter();
            System.out.println("--- HEADER ROW ---");
            promptRow(sheet.getRow(0), df);
            if(sheet.getLastRowNum() >= 1) {
                System.out.println("--- FIRST DATA ROW ---");
                promptRow(sheet.getRow(1), df);
            }
        }
    }
    private static void promptRow(Row row, DataFormatter df) {
        if(row == null) {
            System.out.println("Row is null");
            return;
        }
        for (int i=0; i<row.getLastCellNum(); i++) {
            System.out.println("Col " + i + ": " + df.formatCellValue(row.getCell(i)));
        }
    }
}
