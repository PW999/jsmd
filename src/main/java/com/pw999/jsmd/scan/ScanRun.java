package com.pw999.jsmd.scan;

import com.pw999.jsmd.queue.QueueName;
import com.pw999.jsmd.queue.WorkQueue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State of one scan. Files are collected while the roots are walked (unchanged ones are only marked as seen, the
 * others are hashed), and {@link #reconcile} then compares everything with the index. Moves and removals can only be
 * told apart once the whole tree has been seen, so no decision about a new or missing file is taken before that.
 */
final class ScanRun implements WalkListener {

    private static final Logger log = LoggerFactory.getLogger(ScanRun.class);
    private static final int BATCH_SIZE = 500;

    private record Entry(Path path, long size, long modifiedAtMs) {
    }

    private record Candidate(String path, long size, long modifiedAtMs, ContentHash hash, FileIndex.Location known) {
    }

    private final long scanId;
    private final FileIndex index;
    private final FileHasher hasher;
    private final WorkQueue queue;

    private final List<Entry> pending = new ArrayList<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<String> completeRoots = new ArrayList<>();
    private final Set<Long> contentToCheck = new HashSet<>();

    private int added;
    private int changed;
    private int moved;
    private int duplicates;
    private int removed;
    private int touched;
    private int unchanged;
    private int kept;
    private int skippedRoots;

    ScanRun(long scanId, FileIndex index, FileHasher hasher, WorkQueue queue) {
        this.scanId = scanId;
        this.index = index;
        this.hasher = hasher;
        this.queue = queue;
    }

    @Override
    public void file(Path path, long size, long modifiedAtMs) {
        pending.add(new Entry(path, size, modifiedAtMs));
        if (pending.size() >= BATCH_SIZE) {
            flush();
        }
    }

    @Override
    public void keep(Path path) {
        kept++;
        index.markKept(path.toString(), scanId);
    }

    /** Records how a root turned out; only fully walked roots are evaluated for removals and moves. */
    void rootWalked(WalkResult result) {
        flush();
        if (result.complete()) {
            completeRoots.add(prefix(result.root()));
        } else {
            skippedRoots++;
        }
    }

    private static String prefix(Path root) {
        String separator = root.getFileSystem().getSeparator();
        String path = root.toString();
        return path.endsWith(separator) ? path : path + separator;
    }

    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<String, FileIndex.Location> known = index.findByPaths(pending.stream().map(e -> e.path().toString()).toList());
        List<Long> seen = new ArrayList<>();
        for (Entry entry : pending) {
            String path = entry.path().toString();
            FileIndex.Location location = known.get(path);
            if (location != null) {
                seen.add(location.id());
                if (location.size() == entry.size() && location.modifiedAtMs() == entry.modifiedAtMs()) {
                    unchanged++;
                    continue;
                }
            }
            try {
                candidates.add(new Candidate(path, entry.size(), entry.modifiedAtMs(), hasher.hash(entry.path()), location));
            } catch (IOException e) {
                kept++;
                log.warn("Cannot read {}, keeping its previous state: {}", path, e.toString());
            }
        }
        index.markSeen(seen, scanId);
        pending.clear();
    }

    ScanSummary reconcile() {
        flush();
        for (Candidate candidate : candidates) {
            if (candidate.known() != null) {
                knownPath(candidate);
            }
        }
        for (Candidate candidate : candidates) {
            if (candidate.known() == null) {
                newPath(candidate);
            }
        }
        if (!completeRoots.isEmpty()) {
            List<Long> gone = index.deleteUnseen(scanId, completeRoots);
            removed += gone.size();
            contentToCheck.addAll(gone);
        }
        for (long contentId : contentToCheck) {
            if (index.countLocations(contentId) == 0) {
                queue.enqueue(QueueName.DELETE, contentId);
            }
        }
        return new ScanSummary(added, changed, moved, duplicates, removed, touched, unchanged, kept, skippedRoots);
    }

    private void knownPath(Candidate candidate) {
        FileIndex.Location known = candidate.known();
        if (Arrays.equals(known.sha256(), candidate.hash().sha256())) {
            touched++;
            index.updateStat(known.id(), candidate.size(), candidate.modifiedAtMs());
            return;
        }
        changed++;
        long contentId = contentFor(candidate.hash());
        index.repoint(known.id(), contentId, candidate.size(), candidate.modifiedAtMs());
        contentToCheck.add(known.contentId());
    }

    private void newPath(Candidate candidate) {
        var existing = index.findContentId(candidate.hash().sha256());
        if (existing.isEmpty()) {
            added++;
            index.insertLocation(candidate.path(), newContent(candidate.hash()), candidate.size(),
                    candidate.modifiedAtMs(), scanId);
            return;
        }
        long contentId = existing.get();
        var missing = completeRoots.isEmpty() ? Optional.<Long>empty()
                : index.findMissingLocation(contentId, scanId, completeRoots);
        if (missing.isPresent()) {
            moved++;
            index.moveLocation(missing.get(), candidate.path(), candidate.size(), candidate.modifiedAtMs(), scanId);
        } else {
            duplicates++;
            queue.cancelPending(QueueName.DELETE, contentId);
            index.insertLocation(candidate.path(), contentId, candidate.size(), candidate.modifiedAtMs(), scanId);
        }
    }

    /**
     * The id of the content with this hash. New content is queued for processing; content that already exists is
     * reused, which also withdraws a pending delete for it.
     */
    private long contentFor(ContentHash hash) {
        var existing = index.findContentId(hash.sha256());
        if (existing.isPresent()) {
            queue.cancelPending(QueueName.DELETE, existing.get());
            return existing.get();
        }
        return newContent(hash);
    }

    private long newContent(ContentHash hash) {
        long contentId = index.insertContent(hash.sha256(), hash.size());
        queue.enqueue(QueueName.PROCESS, contentId);
        return contentId;
    }
}
