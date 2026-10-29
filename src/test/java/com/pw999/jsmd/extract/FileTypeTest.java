package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileTypeTest {

    @Test
    void pdfExtensionIsPdf() {
        assertThat(FileType.of(Path.of("report.pdf"))).isEqualTo(FileType.PDF);
    }

    @Test
    void uppercasePdfExtensionIsPdf() {
        assertThat(FileType.of(Path.of("REPORT.PDF"))).isEqualTo(FileType.PDF);
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff",
                            "PNG", "JPG", "JPEG", "GIF", "BMP", "TIF", "TIFF"})
    void imageExtensionsAreImage(String ext) {
        assertThat(FileType.of(Path.of("scan." + ext))).isEqualTo(FileType.IMAGE);
    }

    @Test
    void docxIsUnsupported() {
        assertThat(FileType.of(Path.of("notes.docx"))).isEqualTo(FileType.UNSUPPORTED);
    }

    @Test
    void datFileIsUnsupportedEvenWithPdfHeader() {
        // Extension decides — no byte-sniffing
        assertThat(FileType.of(Path.of("scan.dat"))).isEqualTo(FileType.UNSUPPORTED);
    }

    @Test
    void noExtensionIsUnsupported() {
        assertThat(FileType.of(Path.of("Makefile"))).isEqualTo(FileType.UNSUPPORTED);
    }

    @Test
    void pathWithDirectoriesUsesFilename() {
        assertThat(FileType.of(Path.of("/data/docs/report.pdf"))).isEqualTo(FileType.PDF);
    }
}
