package com.pw999.jsmd.document;

/** No document with the requested id is known. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(long id) {
        super("No document with id " + id);
    }
}
