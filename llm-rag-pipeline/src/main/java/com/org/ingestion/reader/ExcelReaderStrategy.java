package com.org.ingestion.reader;

import com.org.ingestion.excel.ExcelDocumentReader;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads Excel workbooks (.xlsx / .xls) using Apache POI, converting each sheet's rows
 * into Markdown-formatted tables so the chunker can preserve structure.
 * Tika is intentionally bypassed here — it would flatten the tabular data into plain text.
 */
@Component
class ExcelReaderStrategy implements DocumentReaderStrategy {

    @Override
    public DocumentType documentType() {
        return DocumentType.EXCEL;
    }

    @Override
    public List<Document> read(Resource resource) {
        return new ExcelDocumentReader(resource).get();
    }
}
