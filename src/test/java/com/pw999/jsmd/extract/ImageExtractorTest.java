package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageExtractorTest {

    private OcrService ocrService;
    private ImageExtractor extractor;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        ocrService = mock(OcrService.class);
        extractor = new ImageExtractor(ocrService);
    }

    private Path writePng(String filename, BufferedImage img) throws IOException {
        Path file = tmp.resolve(filename);
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    private static BufferedImage solidWhite(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private Path multiPageTiff(int pages) throws IOException {
        Path file = tmp.resolve("multi.tiff");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            ImageWriteParam param = writer.getDefaultWriteParam();
            for (int i = 0; i < pages; i++) {
                writer.writeToSequence(new IIOImage(solidWhite(100, 100), null, null), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return file;
    }

    @Test
    void pngWithTextReturnsOcrResult() throws IOException {
        when(ocrService.recogniseImages(eq(1), any())).thenReturn(List.of("recognised text"));
        Path png = writePng("text.png", solidWhite(200, 50));

        ExtractionResult result = extractor.extract(png);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.OCR);
        assertThat(result.text()).isEqualTo("recognised text");
    }

    @Test
    void blankImageReturnsNone() throws IOException {
        when(ocrService.recogniseImages(eq(1), any())).thenReturn(List.of("   "));
        Path png = writePng("blank.png", solidWhite(200, 50));

        ExtractionResult result = extractor.extract(png);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.NONE);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void multiPageTiffRecognisesAllPages() throws IOException {
        when(ocrService.recogniseImages(eq(2), any())).thenReturn(List.of("page one", "page two"));
        Path tiff = multiPageTiff(2);

        ExtractionResult result = extractor.extract(tiff);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.OCR);
        assertThat(result.text()).contains("page one").contains("page two");
    }

    @Test
    void undecodablePngThrowsIoException() throws IOException {
        Path bad = tmp.resolve("bad.png");
        Files.writeString(bad, "not an image");

        assertThatThrownBy(() -> extractor.extract(bad))
                .isInstanceOf(IOException.class);
    }
}
