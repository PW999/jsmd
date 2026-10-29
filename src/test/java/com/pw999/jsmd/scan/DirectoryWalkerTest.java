package com.pw999.jsmd.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryWalkerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant OLD = NOW.minusSeconds(3600);

    private final DirectoryWalker walker = new DirectoryWalker();

    @TempDir
    Path tmp;

    private static class Recorder implements WalkListener {
        final List<Path> files = new ArrayList<>();
        final List<Path> kept = new ArrayList<>();
        final List<Long> sizes = new ArrayList<>();

        @Override
        public void file(Path path, long size, long modifiedAtMs) {
            files.add(path);
            sizes.add(size);
        }

        @Override
        public void keep(Path path) {
            kept.add(path);
        }
    }

    private Path write(Path file, String content, Instant modified) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }

    private Instant settledBefore() {
        return NOW.minusSeconds(5);
    }

    @Test
    void reportsRegularFilesRecursivelyWithSize() throws Exception {
        Path a = write(tmp.resolve("a.txt"), "abc", OLD);
        Path b = write(tmp.resolve("sub/deeper/b.txt"), "hello", OLD);
        Recorder recorder = new Recorder();

        WalkResult result = walker.walk(tmp, settledBefore(), recorder);

        assertThat(result.complete()).isTrue();
        assertThat(recorder.files).containsExactlyInAnyOrder(a, b);
        assertThat(recorder.sizes).containsExactlyInAnyOrder(3L, 5L);
        assertThat(recorder.kept).isEmpty();
    }

    @Test
    void symbolicLinksAreNeitherFollowedNorReported() throws Exception {
        Path outside = Files.createDirectory(tmp.resolve("outside"));
        write(outside.resolve("secret.txt"), "x", OLD);
        Path root = Files.createDirectory(tmp.resolve("root"));
        Path real = write(root.resolve("real.txt"), "real", OLD);
        Files.createSymbolicLink(root.resolve("link-to-file"), real);
        Files.createSymbolicLink(root.resolve("link-to-dir"), outside);
        Recorder recorder = new Recorder();

        WalkResult result = walker.walk(root, settledBefore(), recorder);

        assertThat(result.complete()).isTrue();
        assertThat(recorder.files).containsExactly(real);
        assertThat(recorder.kept).isEmpty();
    }

    @Test
    void unreadableDirectoryIsKeptAndTheRestIsStillWalked() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions are not enforced for root");
        Path root = Files.createDirectory(tmp.resolve("root"));
        Path ok = write(root.resolve("ok.txt"), "ok", OLD);
        Path locked = Files.createDirectory(root.resolve("locked"));
        write(locked.resolve("hidden.txt"), "hidden", OLD);
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        Recorder recorder = new Recorder();
        try {
            WalkResult result = walker.walk(root, settledBefore(), recorder);

            assertThat(result.complete()).isTrue();
            assertThat(recorder.files).containsExactly(ok);
            assertThat(recorder.kept).containsExactly(locked);
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void unreadableRootIsReportedUnavailable() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "permissions are not enforced for root");
        Path root = Files.createDirectory(tmp.resolve("root"));
        write(root.resolve("a.txt"), "a", OLD);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("---------"));
        Recorder recorder = new Recorder();
        try {
            WalkResult result = walker.walk(root, settledBefore(), recorder);

            assertThat(result.complete()).isFalse();
            assertThat(result.problem()).contains("cannot be read");
            assertThat(recorder.files).isEmpty();
            assertThat(recorder.kept).isEmpty();
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void missingRootIsReportedUnavailable() {
        Recorder recorder = new Recorder();

        WalkResult result = walker.walk(tmp.resolve("nope"), settledBefore(), recorder);

        assertThat(result.complete()).isFalse();
        assertThat(result.problem()).isEqualTo("does not exist");
        assertThat(recorder.files).isEmpty();
    }

    @Test
    void rootThatIsAFileIsReportedUnavailable() throws Exception {
        Path file = write(tmp.resolve("file.txt"), "x", OLD);

        WalkResult result = walker.walk(file, settledBefore(), new Recorder());

        assertThat(result.complete()).isFalse();
        assertThat(result.problem()).contains("not a directory");
    }

    @Test
    void recentlyModifiedFileIsKeptNotReported() throws Exception {
        Path settled = write(tmp.resolve("settled.txt"), "old", OLD);
        Path fresh = write(tmp.resolve("fresh.txt"), "new", NOW.minusSeconds(1));
        Recorder recorder = new Recorder();

        WalkResult result = walker.walk(tmp, settledBefore(), recorder);

        assertThat(result.complete()).isTrue();
        assertThat(recorder.files).containsExactly(settled);
        assertThat(recorder.kept).containsExactly(fresh);
    }
}
