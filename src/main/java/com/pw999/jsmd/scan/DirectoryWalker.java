package com.pw999.jsmd.scan;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Walks a directory tree recursively without following symbolic links, which are not indexed. */
@Component
public class DirectoryWalker {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWalker.class);

    /**
     * @param settledBefore files modified at or after this instant are reported as {@link WalkListener#keep kept}
     *                      because they may still be written to
     */
    public WalkResult walk(Path root, Instant settledBefore, WalkListener listener) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            String problem = Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                    ? "not a directory (symbolic links are not followed)"
                    : "does not exist";
            log.warn("Cannot scan {}: {}", root, problem);
            return WalkResult.unavailable(root, problem);
        }
        long cutoff = settledBefore.toEpochMilli();
        RootProblem rootProblem = new RootProblem();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        long modifiedAt = attrs.lastModifiedTime().toMillis();
                        if (modifiedAt >= cutoff) {
                            listener.keep(file);
                        } else {
                            listener.file(file, attrs.size(), modifiedAt);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    if (file.equals(root)) {
                        rootProblem.message = "cannot be read: " + e;
                        return FileVisitResult.TERMINATE;
                    }
                    log.warn("Cannot read {}, keeping its previous state: {}", file, e.toString());
                    listener.keep(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            rootProblem.message = "walk failed: " + e;
        }
        if (rootProblem.message != null) {
            log.warn("Cannot scan {}: {}", root, rootProblem.message);
            return WalkResult.unavailable(root, rootProblem.message);
        }
        return WalkResult.ok(root);
    }

    private static final class RootProblem {
        String message;
    }
}
