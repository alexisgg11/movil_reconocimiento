package com.example.reconocimiento;

import com.example.reconocimiento.api.AttendanceWorkbook;
import com.example.reconocimiento.domain.Models.Attendance;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.List;

class AttendanceWorkbookTest {
    @Test void excelPreservesTextIdsUsesLimaTimeAndDoesNotTurnNamesIntoFormulas()throws Exception{
        var face=new Attendance(1,"01234567","=HYPERLINK(\"x\")",LocalDate.parse("2026-10-31"),Instant.parse("2026-11-01T02:20:30Z"),"FACIAL",8,5,"127.0.0.1",.9345,"","",.8,"ROSTRO");
        var fingerprint=new Attendance(2,"00001234","José Ñahui",face.fecha(),face.instante(),"HUELLA",8,5,"127.0.0.1",null,"","",null,"FICHA");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(AttendanceWorkbook.create(List.of(face,fingerprint))))){
            var sheet=book.getSheetAt(0);var row=sheet.getRow(1);var format=new DataFormatter(java.util.Locale.US);
            assertEquals("01234567",row.getCell(1).getStringCellValue());assertEquals(CellType.STRING,row.getCell(2).getCellType());
            assertEquals(face.nombre(),row.getCell(2).getStringCellValue());
            assertEquals("31/10/2026",format.formatCellValue(row.getCell(3)));assertEquals("21:20:30",format.formatCellValue(row.getCell(4)));
            assertEquals(.9345,row.getCell(6).getNumericCellValue(),.00001);assertEquals("0.00%",row.getCell(6).getCellStyle().getDataFormatString());
            assertNull(sheet.getRow(2).getCell(6));assertNull(sheet.getRow(2).getCell(7));
            assertEquals("Huella",sheet.getRow(2).getCell(5).getStringCellValue());assertTrue(sheet.getPaneInformation().isFreezePane());
        }
    }
    @Test void emptyReportIsAValidWorkbookWithHeadings()throws Exception{
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(AttendanceWorkbook.create(List.of())))){
            assertEquals(0,book.getSheetAt(0).getLastRowNum());assertEquals("DNI",book.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
        }
    }
}
