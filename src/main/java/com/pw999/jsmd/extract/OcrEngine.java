package com.pw999.jsmd.extract;

import java.awt.image.BufferedImage;

/**
 * Recognises text in a single image. Implementations are not thread-safe; create one per extraction run.
 * Callers must {@link #close} the engine to release native resources.
 */
public interface OcrEngine extends AutoCloseable {

    /**
     * Recognises text in the given image.
     *
     * @param image the image to recognise
     * @param dpi   resolution of the image in pixels per inch
     * @return the recognised text (may be blank when no text is found)
     * @throws OcrException if the OCR engine fails
     */
    String recognise(BufferedImage image, int dpi);

    /** Recognises text assuming {@value #DEFAULT_DPI} DPI. */
    default String recognise(BufferedImage image) {
        return recognise(image, DEFAULT_DPI);
    }

    /** Resolution assumed when the source does not specify one. */
    int DEFAULT_DPI = 300;

    /** Releases native resources held by this engine session. */
    @Override
    void close();
}
