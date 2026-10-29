package com.pw999.jsmd.document;

/** One known file path whose extracted text matched a search, with the matching excerpt. */
public record SearchResult(long id, String path, long size, Snippet snippet) {

    public String fileName() {
        return DocumentRow.fileNameOf(path);
    }
}
