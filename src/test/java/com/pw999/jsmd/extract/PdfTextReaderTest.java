package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTextReaderTest {

    private final PdfTextReader reader = new PdfTextReader();

    @TempDir
    Path tmp;

    private Path pdfWithText(String text) throws IOException {
        Path file = tmp.resolve("text.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private Path blankPdf() throws IOException {
        Path file = tmp.resolve("blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(file.toFile());
        }
        return file;
    }

    private Path threePagePdfWithBlankMiddle() throws IOException {
        Path file = tmp.resolve("three.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (String text : new String[] {"First page", null, "Third page"}) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                if (text == null) {
                    continue;
                }
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void readPagesReturnsOneStringPerPageIncludingBlank() throws Exception {
        List<String> pages = reader.readPages(threePagePdfWithBlankMiddle());

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0)).contains("First page");
        assertThat(pages.get(1).strip()).isEmpty();
        assertThat(pages.get(2)).contains("Third page");
    }

    @Test
    void pdfWithTextLayerReturnsText() throws Exception {
        Path pdf = pdfWithText("Hello PDF");
        String result = reader.read(pdf);
        assertThat(result).contains("Hello PDF");
    }

    @Test
    void blankPdfReturnsEmpty() throws Exception {
        Path pdf = blankPdf();
        String result = reader.read(pdf);
        assertThat(result.strip()).isEmpty();
    }
}
