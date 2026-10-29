package com.pw999.jsmd.scan;

import java.nio.file.Path;

/** Receives what a {@link DirectoryWalker} finds, one entry at a time, so the tree is never held in memory. */
public interface WalkListener {

    /** A regular file that has not been modified recently. */
    void file(Path path, long size, long modifiedAtMs);

    /**
     * This path, and anything below it, exists but could not be examined (unreadable) or is not ready (modified too
     * recently). Whatever is known about it must be left as it is: not treated as removed, changed or new.
     */
    void keep(Path path);
}
