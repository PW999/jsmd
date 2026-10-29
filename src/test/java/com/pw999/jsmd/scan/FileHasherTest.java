package com.pw999.jsmd.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHasherTest {

    private final FileHasher hasher = new FileHasher();

    @TempDir
    Path dir;

    @Test
    void emptyFile() throws Exception {
        Path file = Files.write(dir.resolve("empty"), new byte[0]);

        ContentHash hash = hasher.hash(file);

        assertThat(hash.hex()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(hash.size()).isZero();
    }

    @Test
    void smallFile() throws Exception {
        Path file = Files.write(dir.resolve("abc"), "abc".getBytes(StandardCharsets.US_ASCII));

        ContentHash hash = hasher.hash(file);

        assertThat(hash.hex()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hash.size()).isEqualTo(3);
    }

    @Test
    void fileLargerThanTheBuffer() throws Exception {
        byte[] data = new byte[1_000_000];
        Arrays.fill(data, (byte) 'a');
        Path file = Files.write(dir.resolve("million"), data);

        ContentHash hash = hasher.hash(file);

        assertThat(hash.hex()).isEqualTo("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0");
        assertThat(hash.size()).isEqualTo(1_000_000);
    }

    @Test
    void identicalContentGivesIdenticalHash() throws Exception {
        Path a = Files.writeString(dir.resolve("a.txt"), "same");
        Path b = Files.writeString(dir.resolve("b.txt"), "same");

        assertThat(hasher.hash(a).sha256()).isEqualTo(hasher.hash(b).sha256());
    }
}
