package com.pw999.jsmd.document;

import java.nio.file.Path;

/** One known file path as shown in the document list; never carries the extracted text. */
public record DocumentRow(long id, String path, long size, ExtractionState state) {

    public String fileName() {
        return fileNameOf(path);
    }

    static String fileNameOf(String path) {
        Path name = Path.of(path).getFileName();
        return name != null ? name.toString() : path;
    }
}
