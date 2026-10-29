package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OcrServiceTest {

    // --- Language mapping ---

    @ParameterizedTest
    @CsvSource({
            "en,  eng",
            "nl,  nld",
            "de,  deu",
            "fr,  fra",
            "eng, eng",   // three-letter passes through
            "nld, nld",
            "zz,  zz"     // unknown two-letter passes through unchanged
    })
    void toTesseractCodeMapsCorrectly(String input, String expected) {
        assertThat(OcrService.toTesseractCode(input)).isEqualTo(expected.strip());
    }

    @Test
    void buildJoinedLanguagesSingleCode() {
        OcrService svc = service("en");
        assertThat(svc.buildJoinedLanguages()).isEqualTo("eng");
    }

    @Test
    void buildJoinedLanguagesMultipleCodes() {
        OcrService svc = service("en", "nl");
        assertThat(svc.buildJoinedLanguages()).isEqualTo("eng+nld");
    }

    // --- Missing language data ---

    @Test
    void missingLanguageDataThrowsOcrException(@org.junit.jupiter.api.io.TempDir Path tmpDir) {
        // tessdata-path points to an empty directory; no .traineddata files
        ExtractProperties props = props(tmpDir, "en");
        OcrService svc = new OcrService(props);

        assertThatThrownBy(() -> svc.recogniseImage(blankImage()))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("eng.traineddata");
    }

    // --- Selective page OCR ---

    @Test
    void recognisePdfPagesRendersOnlyRequestedPagesInRequestedOrder(@org.junit.jupiter.api.io.TempDir Path tmpDir)
            throws Exception {
        // Page widths (points) identify each page: 100, 200, 300, 400
        Path pdf = tmpDir.resolve("sizes.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int width = 100; width <= 400; width += 100) {
                doc.addPage(new PDPage(new PDRectangle(width, 100)));
            }
            doc.save(pdf.toFile());
        }
        List<Integer> recognisedWidths = new ArrayList<>();
        int[] sessions = {0};
        OcrService svc = new OcrService(props(null, "en")) {
            @Override
            OcrEngine createEngine(String joinedLangs) {
                sessions[0]++;
                return new OcrEngine() {
                    @Override
                    public String recognise(BufferedImage image, int dpi) {
                        recognisedWidths.add(image.getWidth());
                        return "w" + image.getWidth();
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };

        List<String> texts = svc.recognisePdfPages(pdf, List.of(3, 1));

        assertThat(sessions[0]).isEqualTo(1);
        assertThat(recognisedWidths).hasSize(2);
        assertThat(texts).hasSize(2);
        // 300 dpi / 72 pt: page 3 (index 3) is wider than page 1 (index 1), and order follows the request
        assertThat(recognisedWidths.get(0)).isGreaterThan(recognisedWidths.get(1));
        assertThat(texts).containsExactly("w" + recognisedWidths.get(0), "w" + recognisedWidths.get(1));
    }

    // --- helper factories ---

    private static OcrService service(String... languages) {
        return new OcrService(props(null, languages));
    }

    private static ExtractProperties props(Path tessdataPath, String... languages) {
        return new ExtractProperties(
                true,
                Duration.ofSeconds(10),
                List.of(languages),
                tessdataPath,
                "libtesseract.so.5.5",
                300);
    }

    private static BufferedImage blankImage() {
        return new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    }
}
