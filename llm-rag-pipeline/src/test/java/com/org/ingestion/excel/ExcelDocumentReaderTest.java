package com.org.ingestion.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.FileSystemResource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelDocumentReaderTest {

    private static void writeRow(Sheet sheet, int rowNum, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c]);
        }
    }

    @Test
    @DisplayName("Renders an Excel sheet's header and data rows as a markdown table")
    void rendersSheetAsMarkdownTable() throws Exception {
        Path file = Files.createTempFile("test-", ".xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Leave");
            writeRow(sheet, 0, "Type", "Days");
            writeRow(sheet, 1, "Annual", "20");
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }

            List<Document> docs = new ExcelDocumentReader(new FileSystemResource(file)).get();

            assertThat(docs).hasSize(1);
            String md = docs.get(0).getText();
            assertThat(md).contains("| Type | Days |");   // header row
            assertThat(md).contains("| --- | --- |");      // markdown separator
            assertThat(md).contains("| Annual | 20 |");    // data row
            assertThat(docs.get(0).getMetadata()).containsEntry("sheet", "Leave");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
