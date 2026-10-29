package com.pw999.jsmd.document;

/** The recorded path is gone, is not a readable regular file, or is a symbolic link. */
public class FileUnavailableException extends RuntimeException {

    public FileUnavailableException(String message) {
        super(message);
    }

    public FileUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
