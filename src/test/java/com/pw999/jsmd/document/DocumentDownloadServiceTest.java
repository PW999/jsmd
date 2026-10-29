package com.pw999.jsmd.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pw999.jsmd.scan.FileHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

class DocumentDownloadServiceTest {

    @TempDir
    Path dir;

    DocumentRepository repo = mock(DocumentRepository.class);
    DocumentDownloadService service = new DocumentDownloadService(repo, new FileHasher());

    byte[] content;

    @BeforeEach
    void setUp() {
        content = "file bytes".getBytes();
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private void know(long id, Path path, byte[] sha) {
        when(repo.find(id)).thenReturn(Optional.of(new DocumentDetail(
                id, path.toString(), content.length, ExtractionState.TEXT_LAYER, "t", 1, sha)));
    }

    @Test
    void verifiedFileIsReturnedWithNameAndType() throws Exception {
        Path file = Files.write(dir.resolve("report.pdf"), content);
        know(1, file, sha256(content));

        DocumentDownload download = service.prepare(1);

        assertThat(download.fileName()).isEqualTo("report.pdf");
        assertThat(download.contentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(download.resource().getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void unknownDocumentIsRejected() {
        when(repo.find(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(9)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void deletedFileIsUnavailable() throws Exception {
        know(1, dir.resolve("gone.pdf"), sha256(content));

        assertThatThrownBy(() -> service.prepare(1)).isInstanceOf(FileUnavailableException.class);
    }

    @Test
    void directoryIsUnavailable() throws Exception {
        know(1, Files.createDirectory(dir.resolve("folder.pdf")), sha256(content));

        assertThatThrownBy(() -> service.prepare(1)).isInstanceOf(FileUnavailableException.class);
    }

    @Test
    void modifiedFileIsRefused() throws Exception {
        Path file = Files.write(dir.resolve("report.pdf"), content);
        know(1, file, sha256(content));
        Files.write(file, "different".getBytes());

        assertThatThrownBy(() -> service.prepare(1)).isInstanceOf(FileChangedException.class);
    }

    @Test
    void symbolicLinkIsRefused() throws Exception {
        Path target = Files.write(dir.resolve("real.pdf"), content);
        Path link = Files.createSymbolicLink(dir.resolve("link.pdf"), target);
        know(1, link, sha256(content));

        assertThatThrownBy(() -> service.prepare(1)).isInstanceOf(FileUnavailableException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "a.pdf,application/pdf", "a.PDF,application/pdf", "a.png,image/png", "a.jpg,image/jpeg",
            "a.jpeg,image/jpeg", "a.gif,image/gif", "a.bmp,image/bmp", "a.tif,image/tiff",
            "a.tiff,image/tiff", "a.txt,application/octet-stream", "noext,application/octet-stream"})
    void contentTypeFollowsExtension(String name, String type) {
        assertThat(DocumentDownloadService.contentTypeOf(name)).isEqualTo(MediaType.parseMediaType(type));
    }
}
