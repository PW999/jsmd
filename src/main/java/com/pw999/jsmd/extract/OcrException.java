package com.pw999.jsmd.extract;

/** Thrown when the OCR engine encounters an unrecoverable error. */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
