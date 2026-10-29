package com.pw999.jsmd.extract;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the text-extraction consumer.
 *
 * @param enabled          master switch; nothing is claimed when false
 * @param pollInterval     delay between the end of one drain pass and the start of the next
 * @param ocrLanguages     ISO 639-1 (two-letter) or Tesseract (three-letter) language codes; defaults to {@code en}
 * @param tessdataPath     optional explicit tessdata directory; falls back to TESSDATA_PREFIX / Tesseract's default
 * @param tesseractLibrary native Tesseract library name or absolute path
 * @param renderDpi        DPI used when rendering PDF pages for OCR; minimum 72
 */
@Validated
@ConfigurationProperties("jsmd.extract")
public record ExtractProperties(
        boolean enabled,
        @NotNull Duration pollInterval,
        @NotEmpty List<String> ocrLanguages,
        Path tessdataPath,
        @NotNull String tesseractLibrary,
        @Min(72) int renderDpi) {

    public ExtractProperties {
        ocrLanguages = ocrLanguages == null ? List.of("en") : List.copyOf(ocrLanguages);
    }
}
