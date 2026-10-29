package com.pw999.jsmd.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pw999.jsmd.queue.QueueName;
import com.pw999.jsmd.queue.WorkItem;
import com.pw999.jsmd.queue.WorkQueue;
import com.pw999.jsmd.scan.FileHasher;

/**
 * Claims one item from the PROCESS queue, extracts text, stores the result, and marks the item done.
 * Failures call {@link WorkQueue#fail} so the queue's retry and permanent-failure rules apply.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final WorkQueue workQueue;
    private final ContentTextRepository repository;
    private final PdfExtractor pdfExtractor;
    private final ImageExtractor imageExtractor;
    private final FileHasher fileHasher;

    public ExtractionService(WorkQueue workQueue,
                             ContentTextRepository repository,
                             PdfExtractor pdfExtractor,
                             ImageExtractor imageExtractor,
                             FileHasher fileHasher) {
        this.workQueue = workQueue;
        this.repository = repository;
        this.pdfExtractor = pdfExtractor;
        this.imageExtractor = imageExtractor;
        this.fileHasher = fileHasher;
    }

    /**
     * Claims and processes one item from the PROCESS queue.
     *
     * @return {@code true} if an item was claimed and processed (success or failure); {@code false} if the queue was empty
     */
    public boolean processNext() {
        return workQueue.claim(QueueName.PROCESS).map(this::process).orElse(false);
    }

    private boolean process(WorkItem item) {
        log.debug("Processing content_id={} (attempt {})", item.contentId(), item.attempts());
        try {
            List<String> paths = repository.findFilePaths(item.contentId());
            Path readablePath = firstMatching(paths, item.contentId());

            FileType type = FileType.of(readablePath);
            ExtractionResult result = switch (type) {
                case PDF -> pdfExtractor.extract(readablePath);
                case IMAGE -> imageExtractor.extract(readablePath);
                case UNSUPPORTED -> null; // complete without storing
            };

            if (result != null) {
                repository.upsert(item.contentId(), result.text(), result.source(), null);
            }

            boolean completed = workQueue.complete(item.id());
            if (!completed) {
                log.warn("Item {} was not in CLAIMED state when completing; claim may have timed out", item.id());
            }
            return true;

        } catch (Exception e) {
            log.warn("Extraction failed for content_id={}: {}", item.contentId(), e.getMessage());
            workQueue.fail(item.id(), e.getMessage());
            return true;
        } catch (Error e) {
            // OutOfMemoryError, UnsatisfiedLinkError, ...: release the claim for retry, then let the Error propagate
            log.error("Extraction aborted for content_id={}", item.contentId(), e);
            workQueue.fail(item.id(), e.toString());
            throw e;
        }
    }

    /**
     * Returns the first path in the list that exists, is readable, and whose bytes still match the
     * content's recorded SHA-256.
     *
     * @throws IOException if no path qualifies
     */
    private Path firstMatching(List<String> paths, long contentId) throws IOException {
        byte[] expected = repository.findSha256(contentId)
                .orElseThrow(() -> new IOException("No content row found for content_id=" + contentId));
        int changed = 0;
        for (String p : paths) {
            Path path = Path.of(p);
            if (!Files.isReadable(path)) {
                continue;
            }
            try {
                if (MessageDigest.isEqual(expected, fileHasher.hash(path).sha256())) {
                    return path;
                }
                changed++;
                log.debug("File {} no longer matches the recorded hash of content_id={}", path, contentId);
            } catch (IOException e) {
                log.debug("Could not hash {} for content_id={}: {}", path, contentId, e.getMessage());
            }
        }
        throw new IOException("No readable path with matching hash found for content_id=" + contentId
                + " (checked " + paths.size() + " location(s), " + changed + " changed since scan)");
    }
}
