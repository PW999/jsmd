package com.pw999.jsmd.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Creates the configured scan directories under the {@code dev} profile, so the git-ignored
 * {@code dev-documents} directory exists on a fresh checkout.
 */
@Component
@Profile("dev")
class DevScanDirectories {

    private static final Logger log = LoggerFactory.getLogger(DevScanDirectories.class);

    DevScanDirectories(ScanProperties properties) {
        for (Path directory : properties.directories()) {
            if (Files.notExists(directory)) {
                try {
                    Files.createDirectories(directory);
                    log.info("Created scan directory {}", directory);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot create scan directory " + directory, e);
                }
            }
        }
    }
}
