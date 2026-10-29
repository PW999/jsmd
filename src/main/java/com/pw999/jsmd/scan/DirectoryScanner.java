package com.pw999.jsmd.scan;

import com.pw999.jsmd.queue.WorkQueue;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scans the configured directories and reconciles them with the file index. A scan is one transaction: either the
 * index and the queues reflect the whole scan, or nothing changed.
 */
@Service
public class DirectoryScanner implements ScanTask {

    private static final Logger log = LoggerFactory.getLogger(DirectoryScanner.class);

    private final ScanProperties properties;
    private final DirectoryWalker walker;
    private final FileHasher hasher;
    private final FileIndex index;
    private final WorkQueue queue;
    private final TransactionTemplate transaction;

    public DirectoryScanner(ScanProperties properties, DirectoryWalker walker, FileHasher hasher, FileIndex index,
                            WorkQueue queue, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.walker = walker;
        this.hasher = hasher;
        this.index = index;
        this.queue = queue;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run() {
        scan();
    }

    public ScanSummary scan() {
        Instant settledBefore = Instant.now().minus(properties.settleTime());
        ScanSummary summary = transaction.execute(status -> {
            ScanRun run = new ScanRun(index.nextScanId(), index, hasher, queue);
            for (var root : properties.directories()) {
                run.rootWalked(walker.walk(root, settledBefore, run));
            }
            return run.reconcile();
        });
        log.info("Scan finished: {} new, {} changed, {} moved, {} duplicate, {} removed "
                        + "({} unchanged, {} touched, {} kept, {} root(s) skipped)",
                summary.added(), summary.changed(), summary.moved(), summary.duplicates(), summary.removed(),
                summary.unchanged(), summary.touched(), summary.kept(), summary.skippedRoots());
        return summary;
    }
}
