package com.pw999.jsmd.extract;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Extracts text from image files by running OCR via {@link OcrService}.
 *
 * <p>Multi-page TIFFs are read page by page; each page is recognised separately and the results are joined.
 * An image that yields no text is not an error — it completes with {@link ExtractionResult.Source#NONE}.
 *
 * @throws IOException if the file cannot be decoded as an image
 */
@Component
public class ImageExtractor {

    private final OcrService ocrService;

    public ImageExtractor(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /**
     * Runs OCR on the given image file.
     *
     * @param path path to an image file
     * @return extraction result with recognised text and source indicator
     * @throws IOException if the image cannot be decoded
     */
    public ExtractionResult extract(Path path) throws IOException {
        List<String> pageTexts = recogniseAllPages(path);
        String combined = String.join("\f\n", pageTexts);

        if (combined.isBlank()) {
            return new ExtractionResult("", ExtractionResult.Source.NONE);
        }
        return new ExtractionResult(combined, ExtractionResult.Source.OCR);
    }

    private List<String> recogniseAllPages(Path path) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
            if (iis == null) {
                throw new IOException("Cannot read image: " + path);
            }
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("No ImageReader available for: " + path);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, true);
                int numImages = reader.getNumImages(true);
                try {
                    return ocrService.recogniseImages(numImages, i -> {
                        try {
                            return reader.read(i);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
