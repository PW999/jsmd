package com.pw999.jsmd.scan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** Computes SHA-256 content hashes, streaming the file through a fixed buffer. */
@Component
public class FileHasher {

    private static final int BUFFER_SIZE = 64 * 1024;

    public ContentHash hash(Path file) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        long size = 0;
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        return new ContentHash(digest.digest(), size);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
