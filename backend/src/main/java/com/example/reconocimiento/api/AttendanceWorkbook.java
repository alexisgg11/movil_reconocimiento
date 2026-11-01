package com.example.reconocimiento.api;

import com.example.reconocimiento.domain.Models.Attendance;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

public final class AttendanceWorkbook {
    private AttendanceWorkbook() {}
    public static byte[] create(List<Attendance> rows) throws IOException {
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=book.createSheet("Asistencias");
            var headerStyle=book.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());headerStyle.setFont(font);
            var percentage=book.createCellStyle();percentage.setDataFormat(book.createDataFormat().getFormat("0.00%"));
            var meters=book.createCellStyle();meters.setDataFormat(book.createDataFormat().getFormat("0.0"));
            var dateStyle=book.createCellStyle();dateStyle.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
            var timeStyle=book.createCellStyle();timeStyle.setDataFormat(book.createDataFormat().getFormat("hh:mm:ss"));
            String[] headings={"ID","DNI","Persona","Fecha","Hora (Lima)","Método","Similitud","Mínimo requerido","Distancia (m)","Precisión GPS (m)","IP de conexión"};
            var header=sheet.createRow(0);header.setHeightInPoints(26);
            for(int i=0;i<headings.length;i++){var c=header.createCell(i);c.setCellValue(headings[i]);c.setCellStyle(headerStyle);}
            int number=1;
            for(var a:rows){
                var row=sheet.createRow(number++);
                row.createCell(0).setCellValue(a.id());
                row.createCell(1).setCellValue(a.dni());
                row.createCell(2).setCellValue(a.nombre());
                var local=a.instante().atZone(ZoneId.of("America/Lima")).toLocalDateTime();
                var date=row.createCell(3);date.setCellValue(a.fecha().atStartOfDay());date.setCellStyle(dateStyle);
                var time=row.createCell(4);time.setCellValue(local);time.setCellStyle(timeStyle);
                row.createCell(5).setCellValue(a.metodo().equals("FACIAL")?"Facial":"Huella");
                if(a.similitud()!=null){var cell=row.createCell(6);cell.setCellValue(a.similitud());cell.setCellStyle(percentage);}
                if(a.umbral()!=null){var cell=row.createCell(7);cell.setCellValue(a.umbral());cell.setCellStyle(percentage);}
                var distance=row.createCell(8);distance.setCellValue(a.distancia());distance.setCellStyle(meters);
                var accuracy=row.createCell(9);accuracy.setCellValue(a.precision());accuracy.setCellStyle(meters);
                row.createCell(10).setCellValue(a.ip());
            }
            int[] widths={10,14,42,16,18,14,16,20,20,23,22};
            for(int i=0;i<widths.length;i++)sheet.setColumnWidth(i,widths[i]*256);
            sheet.createFreezePane(0,1);
            sheet.setAutoFilter(new CellRangeAddress(0,Math.max(0,number-1),0,headings.length-1));
            book.getProperties().getCoreProperties().setTitle("Reporte de asistencias");
            book.getProperties().getCoreProperties().setDescription("Marcaciones registradas. Horario de Lima. Los porcentajes son puntajes del modelo, no probabilidades.");
            book.write(output);return output.toByteArray();
        }
    }
}
