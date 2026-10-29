package com.pw999.jsmd.document;

/** The file on disk no longer matches the SHA-256 recorded when it was indexed. */
public class FileChangedException extends RuntimeException {

    public FileChangedException(String message) {
        super(message);
    }
}
