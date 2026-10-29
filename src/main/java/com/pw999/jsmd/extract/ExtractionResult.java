package com.pw999.jsmd.extract;

/**
 * The outcome of extracting text from a single content file.
 *
 * @param text   extracted text (may be blank when source is {@link Source#NONE})
 * @param source how the text was obtained
 */
public record ExtractionResult(String text, Source source) {

    /** How the text was obtained. Maps directly to the {@code source} check constraint in {@code content_text}. */
    public enum Source {
        /** Extracted from the PDF embedded text layer. */
        TEXT_LAYER,
        /** Produced by OCR. */
        OCR,
        /** No text found; the file was processed but contained no recognisable text. */
        NONE
    }
}
