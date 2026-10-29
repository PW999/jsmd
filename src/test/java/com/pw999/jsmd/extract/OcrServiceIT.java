package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test that runs OCR on a generated image-only PDF.
 * Skipped when Tesseract is not installed.
 */
class OcrServiceIT {

    private static final String LIBRARY = System.getProperty("jsmd.tesseract.library", "libtesseract.so.5.5");
    private static boolean available;

    @BeforeAll
    static void checkLibraryAvailable() {
        try {
            try (TesseractOcrEngine e = new TesseractOcrEngine(LIBRARY, null, "eng")) {
                available = true;
            }
        } catch (OcrException ignored) {
            available = false;
        }
    }

    private static void assumeAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(available,
                "Tesseract not available (" + LIBRARY + "); skipping OcrService IT");
    }

    @TempDir
    Path tmp;

    /** Creates a PDF whose single page is an embedded raster image (text rendered into a bitmap). */
    private Path imagePdf(String text) throws Exception {
        // Render text onto a BufferedImage
        BufferedImage img = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.PLAIN, 36));
        g.drawString(text, 20, 100);
        g.dispose();

        // Embed that image in a PDF page (no text layer)
        Path file = tmp.resolve("ocr.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            var pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 0, 600, 400, 100);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void recognisesTextInImageOnlyPdf() throws Exception {
        assumeAvailable();
        ExtractProperties props = new ExtractProperties(
                true, Duration.ofSeconds(10), List.of("en"), null, LIBRARY, 150);
        OcrService svc = new OcrService(props);

        Path pdf = imagePdf("OCR test");
        String result = svc.recognisePdf(pdf);
        assertThat(result).containsIgnoringCase("ocr");
    }
}
