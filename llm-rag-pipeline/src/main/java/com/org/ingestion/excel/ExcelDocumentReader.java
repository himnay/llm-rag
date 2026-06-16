package com.org.ingestion.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Excel workbook (xlsx/xls) into one {@link Document} per sheet, rendering each sheet as a
 * <b>Markdown table</b> so the tabular structure (columns, header row) survives into chunking and
 * retrieval — unlike Tika, which flattens spreadsheets into an unstructured text stream.
 */
@Slf4j
public class ExcelDocumentReader implements DocumentReader {

    private final Resource resource;
    private final DataFormatter formatter = new DataFormatter();

    public ExcelDocumentReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public List<Document> get() {
        List<Document> documents = new ArrayList<>();
        try (InputStream in = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String markdown = toMarkdown(sheet);
                if (!markdown.isBlank()) {
                    documents.add(new Document(markdown, java.util.Map.of(
                            "sheet", sheet.getSheetName(),
                            "contentType", "table")));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Excel workbook: " + e.getMessage(), e);
        }
        return documents;
    }

    private String toMarkdown(Sheet sheet) {
        int lastRow = sheet.getLastRowNum();
        if (lastRow < 0) {
            return "";
        }
        int columns = 0;
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                columns = Math.max(columns, row.getLastCellNum());
            }
        }
        if (columns <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder("## ").append(sheet.getSheetName()).append("\n\n");
        for (int r = 0; r <= lastRow; r++) {
            sb.append(renderRow(sheet.getRow(r), columns)).append('\n');
            if (r == 0) {
                sb.append("| ").append("--- | ".repeat(columns)).append('\n');
            }
        }
        return sb.toString();
    }

    private String renderRow(Row row, int columns) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < columns; c++) {
            String value = "";
            if (row != null) {
                Cell cell = row.getCell(c);
                if (cell != null) {
                    value = formatter.formatCellValue(cell).replace("|", "\\|").replace("\n", " ").trim();
                }
            }
            sb.append(' ').append(value).append(" |");
        }
        return sb.toString();
    }
}
