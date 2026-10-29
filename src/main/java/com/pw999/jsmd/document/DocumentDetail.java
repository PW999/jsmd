package com.pw999.jsmd.document;

/** A known file path with its extracted text and the SHA-256 recorded for its content. */
public record DocumentDetail(long id, String path, long size, ExtractionState state, String text,
                             Integer pageCount, byte[] sha256) {

    public String fileName() {
        return DocumentRow.fileNameOf(path);
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
