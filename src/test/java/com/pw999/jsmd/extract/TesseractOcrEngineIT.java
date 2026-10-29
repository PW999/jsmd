package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link TesseractOcrEngine}.
 * Skipped automatically when the Tesseract native library cannot be loaded.
 */
class TesseractOcrEngineIT {

    private static final String LIBRARY = System.getProperty("jsmd.tesseract.library", "libtesseract.so.5.5");
    private static boolean available;

    @BeforeAll
    static void checkLibraryAvailable() {
        try {
            try (TesseractOcrEngine engine = new TesseractOcrEngine(LIBRARY, null, "eng")) {
                available = true;
            }
        } catch (OcrException e) {
            available = false;
        }
    }

    private static void assumeAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(available,
                "Tesseract library not available (" + LIBRARY + "); skipping OCR engine tests");
    }

    /** Renders the given text as a simple white-on-black bitmap. */
    private static BufferedImage textImage(String text) {
        BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.PLAIN, 36));
        g.drawString(text, 20, 60);
        g.dispose();
        return img;
    }

    @Test
    void recognisesTextInGeneratedImage() {
        assumeAvailable();
        try (TesseractOcrEngine engine = new TesseractOcrEngine(LIBRARY, null, "eng")) {
            String result = engine.recognise(textImage("Hello"));
            assertThat(result).containsIgnoringCase("hello");
        }
    }

    @Test
    void createRecogniseDisposeLoopDoesNotCrash() {
        assumeAvailable();
        BufferedImage img = textImage("Test");
        for (int i = 0; i < 5; i++) {
            try (TesseractOcrEngine engine = new TesseractOcrEngine(LIBRARY, null, "eng")) {
                String result = engine.recognise(img);
                assertThat(result).isNotNull();
            }
        }
    }
}
