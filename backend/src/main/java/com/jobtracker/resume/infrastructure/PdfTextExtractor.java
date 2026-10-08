package com.jobtracker.resume.infrastructure;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts plain text from PDF bytes using Apache PDFBox.
 * Returns null on any failure — the resume upload still succeeds without text.
 */
@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    /**
     * @param pdfBytes raw PDF bytes
     * @return extracted text, or null if extraction failed or the PDF is empty
     */
    public String extract(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) {
                log.warn("PDF has zero pages — no text extracted");
                return null;
            }
            String text = new PDFTextStripper().getText(doc);
            return text.isBlank() ? null : text.trim();
        } catch (Exception e) {
            log.warn("PDF text extraction failed: {}", e.getMessage());
            return null;
        }
    }
}
