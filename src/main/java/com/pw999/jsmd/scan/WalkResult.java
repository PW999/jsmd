package com.pw999.jsmd.scan;

import java.nio.file.Path;

/**
 * Outcome of walking one root.
 *
 * @param complete true if every entry below the root was either reported or kept; only then may files of that root
 *                 that were not reported be considered removed
 * @param problem  why the root could not be walked, when it is not complete
 */
public record WalkResult(Path root, boolean complete, String problem) {

    public static WalkResult ok(Path root) {
        return new WalkResult(root, true, null);
    }

    public static WalkResult unavailable(Path root, String problem) {
        return new WalkResult(root, false, problem);
    }
}
