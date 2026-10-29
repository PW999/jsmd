package com.pw999.jsmd.scan;

/**
 * What one scan found.
 *
 * @param added       new files with new content (queued for processing)
 * @param changed     known paths whose content changed
 * @param moved       files found at a new path whose old path is gone
 * @param duplicates  new paths holding content that already exists elsewhere
 * @param removed     known paths no longer on disk
 * @param touched     known paths with a new modification time but identical content
 * @param unchanged   known paths skipped because size and modification time are as before
 * @param kept        paths left alone because they could not be read or were modified too recently
 * @param skippedRoots roots that could not be walked and were therefore not evaluated
 */
public record ScanSummary(int added, int changed, int moved, int duplicates, int removed, int touched, int unchanged,
                          int kept, int skippedRoots) {

    public static final ScanSummary EMPTY = new ScanSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
}
