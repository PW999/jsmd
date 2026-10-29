package com.pw999.jsmd.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.pw999.jsmd.queue.QueueName;
import com.pw999.jsmd.queue.QueueProperties;
import com.pw999.jsmd.queue.WorkQueue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DirectoryScannerIT {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @TempDir
    Path tmp;

    JdbcClient jdbc;
    WorkQueue queue;
    AtomicInteger hashed;
    DirectoryScanner scanner;
    Path root;
    Path root2;
    int clock;

    @BeforeEach
    void setUp() throws IOException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        root = Files.createDirectory(tmp.resolve("root"));
        root2 = Files.createDirectory(tmp.resolve("root2"));
        scannerFor(root, root2);
    }

    private void scannerFor(Path... roots) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        queue = new WorkQueue(jdbc, new QueueProperties(Duration.ZERO, 3, Duration.ofMinutes(15), Duration.ofDays(7), "0 30 3 * * *"));
        hashed = new AtomicInteger();
        FileHasher countingHasher = new FileHasher() {
            @Override
            public ContentHash hash(Path file) throws IOException {
                hashed.incrementAndGet();
                return super.hash(file);
            }
        };
        ScanProperties properties = new ScanProperties(true, List.of(roots), Duration.ofMinutes(5), Duration.ZERO);
        scanner = new DirectoryScanner(properties, new DirectoryWalker(), countingHasher, new FileIndex(jdbc), queue,
                new DataSourceTransactionManager(dataSource));
    }

    // ---- file helpers -------------------------------------------------------------------------------------------

    /** Writes a file with a modification time that is always later than the previous write, and in the past. */
    private Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(BASE.plusSeconds(++clock)));
        return file;
    }

    private Path write(String relative, String content) throws IOException {
        return write(root.resolve(relative), content);
    }

    private void move(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }

    private void copy(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.copy(from, to);
        Files.setLastModifiedTime(to, FileTime.from(BASE.plusSeconds(++clock)));
    }

    // ---- database helpers ---------------------------------------------------------------------------------------

    private static String sha(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Known paths (relative to the temp dir) mapped to the hash of their content. */
    private Map<String, String> locations() {
        Map<String, String> result = new TreeMap<>();
        jdbc.sql("SELECT l.path, encode(c.sha256, 'hex') AS sha FROM file_location l JOIN content c ON c.id = l.content_id")
                .query((rs, rowNum) -> {
                    result.put(tmp.relativize(Path.of(rs.getString("path"))).toString(), rs.getString("sha"));
                    return null;
                }).list();
        return result;
    }

    private int contentCount() {
        return jdbc.sql("SELECT count(*) FROM content").query(Integer.class).single();
    }

    /** Hashes of the content that has an item with this status on this queue. */
    private Set<String> items(QueueName queueName, String status) {
        return new TreeSet<>(jdbc.sql("""
                        SELECT encode(c.sha256, 'hex') FROM work_item w JOIN content c ON c.id = w.content_id
                        WHERE w.queue = ? AND w.status = ?
                        """)
                .param(queueName.name()).param(status).query(String.class).list());
    }

    private int itemCount() {
        return jdbc.sql("SELECT count(*) FROM work_item").query(Integer.class).single();
    }

    private static Set<String> shas(String... contents) {
        Set<String> set = new TreeSet<>();
        for (String c : contents) {
            set.add(sha(c));
        }
        return set;
    }

    private static Map<String, String> index(String... pathAndContent) {
        Map<String, String> map = new TreeMap<>();
        for (int i = 0; i < pathAndContent.length; i += 2) {
            map.put(pathAndContent[i], sha(pathAndContent[i + 1]));
        }
        return map;
    }

    // ---- unchanged files (4.1) ----------------------------------------------------------------------------------

    @Test
    void unchangedTreeCausesNoHashingNoQueueItemsAndOnlyLastSeenUpdates() throws Exception {
        write("a.txt", "alpha");
        write("sub/b.txt", "beta");
        write("sub/c.txt", "gamma");
        scanner.scan();
        assertThat(hashed).hasValue(3);
        int itemsBefore = itemCount();
        Map<String, String> before = locations();
        long seenBefore = jdbc.sql("SELECT min(last_seen_scan_id) FROM file_location").query(Long.class).single();

        ScanSummary summary = scanner.scan();

        assertThat(summary.unchanged()).isEqualTo(3);
        assertThat(summary).isEqualTo(new ScanSummary(0, 0, 0, 0, 0, 0, 3, 0, 0));
        assertThat(hashed).hasValue(3);
        assertThat(itemCount()).isEqualTo(itemsBefore);
        assertThat(locations()).isEqualTo(before);
        assertThat(jdbc.sql("SELECT min(last_seen_scan_id) FROM file_location").query(Long.class).single())
                .isGreaterThan(seenBefore);
    }

    // ---- new, changed, touched (4.2) ----------------------------------------------------------------------------

    @Test
    void newFileIsRecordedAndEnqueuedOnTheProcessQueue() throws Exception {
        write("a.txt", "alpha");

        ScanSummary summary = scanner.scan();

        assertThat(summary.added()).isEqualTo(1);
        assertThat(locations()).isEqualTo(index("root/a.txt", "alpha"));
        assertThat(contentCount()).isEqualTo(1);
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha"));
        assertThat(itemCount()).isEqualTo(1);
    }

    @Test
    void rescanAfterFinishedAndOldItemsWereRemovedQueuesNothingUntilContentChanges() throws Exception {
        Path a = write("a.txt", "alpha");
        write("b.txt", "beta");
        scanner.scan();
        jdbc.sql("UPDATE work_item SET status = 'DONE' WHERE content_id = (SELECT id FROM content WHERE sha256 = ?)")
                .param(HexFormat.of().parseHex(sha("alpha"))).update();
        jdbc.sql("DELETE FROM work_item WHERE status = 'DONE'").update();
        jdbc.sql("UPDATE work_item SET created_at = now() - interval '30 days'").update();
        jdbc.sql("DELETE FROM work_item WHERE created_at < now() - interval '7 days'").update();
        assertThat(itemCount()).isZero();

        ScanSummary unchanged = scanner.scan();

        assertThat(unchanged).isEqualTo(new ScanSummary(0, 0, 0, 0, 0, 0, 2, 0, 0));
        assertThat(itemCount()).isZero();

        write(a, "alpha two");
        scanner.scan();

        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha two"));
    }

    @Test
    void modifiedFileRepointsPathAndEnqueuesNewContentAndOrphanedOldContent() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        write(a, "beta");

        ScanSummary summary = scanner.scan();

        assertThat(summary.changed()).isEqualTo(1);
        assertThat(locations()).isEqualTo(index("root/a.txt", "beta"));
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));
    }

    @Test
    void oldContentStillUsedElsewhereIsNotEnqueuedForDeletion() throws Exception {
        Path a = write("a.txt", "alpha");
        write("b.txt", "alpha");
        scanner.scan();
        write(a, "beta");

        ScanSummary summary = scanner.scan();

        assertThat(summary.changed()).isEqualTo(1);
        assertThat(locations()).isEqualTo(index("root/a.txt", "beta", "root/b.txt", "alpha"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "beta"));
    }

    @Test
    void fileChangedToContentThatAlreadyExistsIsNotEnqueuedAgain() throws Exception {
        Path a = write("a.txt", "alpha");
        write("b.txt", "beta");
        scanner.scan();
        write(a, "beta");

        ScanSummary summary = scanner.scan();

        assertThat(summary.changed()).isEqualTo(1);
        assertThat(contentCount()).isEqualTo(2);
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));
    }

    @Test
    void touchedButIdenticalFileEnqueuesNothingAndUpdatesModificationTime() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        int itemsBefore = itemCount();
        write(a, "alpha");
        long expectedMtime = Files.getLastModifiedTime(a).toMillis();

        ScanSummary summary = scanner.scan();

        assertThat(summary.touched()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(itemsBefore);
        assertThat(jdbc.sql("SELECT modified_at_ms FROM file_location").query(Long.class).single()).isEqualTo(expectedMtime);
    }

    // ---- moves and duplicates (4.3) -----------------------------------------------------------------------------

    @Test
    void fileMovedWithinTreeUpdatesPathAndEnqueuesNothing() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        int itemsBefore = itemCount();
        move(a, root.resolve("deep/er/a.txt"));

        ScanSummary summary = scanner.scan();

        assertThat(summary.moved()).isEqualTo(1);
        assertThat(summary.removed()).isZero();
        assertThat(summary.added()).isZero();
        assertThat(locations()).isEqualTo(index("root/deep/er/a.txt", "alpha"));
        assertThat(contentCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(itemsBefore);
    }

    @Test
    void fileMovedBetweenRootsIsAMove() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        int itemsBefore = itemCount();
        move(a, root2.resolve("a.txt"));

        ScanSummary summary = scanner.scan();

        assertThat(summary.moved()).isEqualTo(1);
        assertThat(locations()).isEqualTo(index("root2/a.txt", "alpha"));
        assertThat(itemCount()).isEqualTo(itemsBefore);
    }

    @Test
    void copyOfExistingFileBecomesAnotherLocationOfTheSameContent() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        int itemsBefore = itemCount();
        copy(a, root.resolve("copy/a.txt"));

        ScanSummary summary = scanner.scan();

        assertThat(summary.duplicates()).isEqualTo(1);
        assertThat(summary.moved()).isZero();
        assertThat(locations()).isEqualTo(index("root/a.txt", "alpha", "root/copy/a.txt", "alpha"));
        assertThat(contentCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(itemsBefore);
    }

    @Test
    void twoNewFilesWithTheSameContentAreStoredAndEnqueuedOnce() throws Exception {
        write("a.txt", "alpha");
        write("b.txt", "alpha");

        ScanSummary summary = scanner.scan();

        assertThat(summary.added()).isEqualTo(1);
        assertThat(summary.duplicates()).isEqualTo(1);
        assertThat(contentCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(1);
    }

    @Test
    void moveAndNewFileInTheSameScanAreToldApart() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        move(a, root.resolve("moved.txt"));
        write("new.txt", "beta");

        ScanSummary summary = scanner.scan();

        assertThat(summary.moved()).isEqualTo(1);
        assertThat(summary.added()).isEqualTo(1);
        assertThat(summary.removed()).isZero();
        assertThat(locations()).isEqualTo(index("root/moved.txt", "alpha", "root/new.txt", "beta"));
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();
    }

    @Test
    void copiesRemovedOneByOneKeepTheContentUntilTheLastIsGone() throws Exception {
        Path a = write("a.txt", "alpha");
        Path b = write("b.txt", "alpha");
        Path c = write("c.txt", "alpha");
        scanner.scan();

        Files.delete(a);
        Files.delete(b);
        ScanSummary summary = scanner.scan();

        assertThat(summary.removed()).isEqualTo(2);
        assertThat(locations()).hasSize(1);
        assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();

        Files.delete(c);
        scanner.scan();

        assertThat(locations()).isEmpty();
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));
        assertThat(contentCount()).as("the scanner never deletes content").isEqualTo(1);
    }

    // ---- removals, unavailable roots, reappearing content (4.4) -------------------------------------------------

    @Test
    void removingTheLastLocationEnqueuesDeleteOnce() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        Files.delete(a);

        ScanSummary summary = scanner.scan();

        assertThat(summary.removed()).isEqualTo(1);
        assertThat(locations()).isEmpty();
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));

        scanner.scan();
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));
        assertThat(jdbc.sql("SELECT count(*) FROM work_item WHERE queue = 'DELETE'").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void unavailableRootLeavesItsFilesUntouchedWhileOtherRootsAreStillScanned() throws Exception {
        write("a.txt", "alpha");
        write(root2.resolve("b.txt"), "beta");
        scanner.scan();
        int itemsBefore = itemCount();
        Map<String, String> before = locations();
        Path away = tmp.resolve("root2-away");
        Files.move(root2, away);
        write("new.txt", "gamma");

        ScanSummary summary = scanner.scan();

        assertThat(summary.skippedRoots()).isEqualTo(1);
        assertThat(summary.removed()).isZero();
        assertThat(summary.added()).isEqualTo(1);
        Map<String, String> expected = new TreeMap<>(before);
        expected.putAll(index("root/new.txt", "gamma"));
        assertThat(locations()).isEqualTo(expected);
        assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();
        assertThat(itemCount()).isEqualTo(itemsBefore + 1);

        Files.move(away, root2);
        ScanSummary after = scanner.scan();

        assertThat(after.removed()).isZero();
        assertThat(after.skippedRoots()).isZero();
        assertThat(locations()).isEqualTo(expected);
    }

    @Test
    void fileReappearingBeforeTheDeleteIsClaimedCancelsItWithoutProcessing() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        Files.delete(a);
        scanner.scan();
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));
        int processItems = jdbc.sql("SELECT count(*) FROM work_item WHERE queue = 'PROCESS'").query(Integer.class).single();

        write(a, "alpha");
        ScanSummary summary = scanner.scan();

        assertThat(summary.added()).isZero();
        assertThat(locations()).isEqualTo(index("root/a.txt", "alpha"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();
        assertThat(items(QueueName.DELETE, "CANCELLED")).isEqualTo(shas("alpha"));
        assertThat(jdbc.sql("SELECT count(*) FROM work_item WHERE queue = 'PROCESS'").query(Integer.class).single())
                .isEqualTo(processItems);
        assertThat(contentCount()).isEqualTo(1);
    }

    @Test
    void fileReappearingAfterTheDeleteWasClaimedLeavesTheClaimAlone() throws Exception {
        Path a = write("a.txt", "alpha");
        scanner.scan();
        Files.delete(a);
        scanner.scan();
        queue.claim(QueueName.DELETE).orElseThrow();

        write(a, "alpha");
        scanner.scan();

        assertThat(items(QueueName.DELETE, "CLAIMED")).isEqualTo(shas("alpha"));
        assertThat(locations()).isEqualTo(index("root/a.txt", "alpha"));
    }

    @Test
    void unreadableChangedFileKeepsItsPreviousState() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions are not enforced for root");
        Path a = write("a.txt", "alpha");
        write("b.txt", "beta");
        scanner.scan();
        write(a, "changed");
        Files.setPosixFilePermissions(a, PosixFilePermissions.fromString("---------"));
        try {
            ScanSummary summary = scanner.scan();

            assertThat(summary.kept()).isEqualTo(1);
            assertThat(summary.removed()).isZero();
            assertThat(summary.changed()).isZero();
            assertThat(locations()).isEqualTo(index("root/a.txt", "alpha", "root/b.txt", "beta"));
            assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();
        } finally {
            Files.setPosixFilePermissions(a, PosixFilePermissions.fromString("rw-------"));
        }
    }

    // ---- end to end (4.5) ---------------------------------------------------------------------------------------

    @Test
    void scanThroughCreateModifyMoveCopyAndDelete() throws Exception {
        // create
        Path a = write("docs/a.txt", "alpha");
        Path b = write("docs/b.txt", "beta");
        assertThat(scanner.scan()).isEqualTo(new ScanSummary(2, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(locations()).isEqualTo(index("root/docs/a.txt", "alpha", "root/docs/b.txt", "beta"));
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEmpty();

        // modify
        write(a, "alpha2");
        assertThat(scanner.scan()).isEqualTo(new ScanSummary(0, 1, 0, 0, 0, 0, 1, 0, 0));
        assertThat(locations()).isEqualTo(index("root/docs/a.txt", "alpha2", "root/docs/b.txt", "beta"));
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "alpha2", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));

        // move
        move(b, root.resolve("archive/b.txt"));
        assertThat(scanner.scan()).isEqualTo(new ScanSummary(0, 0, 1, 0, 0, 0, 1, 0, 0));
        assertThat(locations()).isEqualTo(index("root/docs/a.txt", "alpha2", "root/archive/b.txt", "beta"));
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "alpha2", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha"));

        // copy
        copy(root.resolve("archive/b.txt"), root2.resolve("b-copy.txt"));
        assertThat(scanner.scan()).isEqualTo(new ScanSummary(0, 0, 0, 1, 0, 0, 2, 0, 0));
        assertThat(locations()).isEqualTo(index(
                "root/docs/a.txt", "alpha2", "root/archive/b.txt", "beta", "root2/b-copy.txt", "beta"));
        assertThat(items(QueueName.PROCESS, "PENDING")).isEqualTo(shas("alpha", "alpha2", "beta"));

        // delete
        Files.delete(a);
        Files.delete(root.resolve("archive/b.txt"));
        assertThat(scanner.scan()).isEqualTo(new ScanSummary(0, 0, 0, 0, 2, 0, 1, 0, 0));
        assertThat(locations()).isEqualTo(index("root2/b-copy.txt", "beta"));
        assertThat(items(QueueName.DELETE, "PENDING")).isEqualTo(shas("alpha", "alpha2"));
        assertThat(contentCount()).isEqualTo(3);
    }
}
