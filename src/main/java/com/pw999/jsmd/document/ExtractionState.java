package com.pw999.jsmd.document;

/**
 * Where a file's text came from. {@link #PENDING} means no text has been stored for its content yet.
 */
public enum ExtractionState {

    PENDING("Pending"),
    TEXT_LAYER("Text layer"),
    OCR("OCR"),
    NONE("No text");

    private final String label;

    ExtractionState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Maps the nullable {@code content_text.source} column. */
    static ExtractionState of(String source) {
        return source == null ? PENDING : valueOf(source);
    }
}
