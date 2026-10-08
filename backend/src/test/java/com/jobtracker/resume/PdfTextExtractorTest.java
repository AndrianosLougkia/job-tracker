package com.jobtracker.resume;

import com.jobtracker.resume.infrastructure.PdfTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextFromValidPdf() throws Exception {
        byte[] pdf = createPdfWithText("Hello World");
        String result = extractor.extract(pdf);
        assertThat(result).contains("Hello World");
    }

    @Test
    void returnsNullForEmptyPdf() throws Exception {
        // PDF with zero pages
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }
        assertThat(extractor.extract(pdf)).isNull();
    }

    @Test
    void returnsNullForInvalidBytes() {
        byte[] garbage = "not a pdf".getBytes();
        assertThat(extractor.extract(garbage)).isNull();
    }

    @Test
    void returnsNullForBlankPdf() throws Exception {
        // Page with no text
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }
        assertThat(extractor.extract(pdf)).isNull();
    }

    // Helper: creates a minimal PDF with one text line
    private byte[] createPdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
