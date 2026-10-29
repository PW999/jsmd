package com.pw999.jsmd.document;

import com.pw999.jsmd.scan.FileHasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Resolves a document id to the path recorded in the database and checks that the file is still the
 * one that was indexed before handing it out. Request input is only ever an id, never a path.
 */
@Service
public class DocumentDownloadService {

    private static final Map<String, MediaType> TYPES_BY_EXTENSION = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "bmp", MediaType.parseMediaType("image/bmp"),
            "tif", MediaType.parseMediaType("image/tiff"),
            "tiff", MediaType.parseMediaType("image/tiff"));

    private final DocumentRepository documents;
    private final FileHasher hasher;

    public DocumentDownloadService(DocumentRepository documents, FileHasher hasher) {
        this.documents = documents;
        this.hasher = hasher;
    }

    public DocumentDownload prepare(long id) {
        DocumentDetail document = documents.find(id).orElseThrow(() -> new DocumentNotFoundException(id));
        Path path = Path.of(document.path());

        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
            throw new FileUnavailableException("The file is no longer available on disk.");
        }
        byte[] actual;
        try {
            actual = hasher.hash(path).sha256();
        } catch (IOException e) {
            throw new FileUnavailableException("The file could not be read.", e);
        }
        if (!MessageDigest.isEqual(actual, document.sha256())) {
            throw new FileChangedException("The file has changed since it was indexed.");
        }
        return new DocumentDownload(new FileSystemResource(path), document.fileName(),
                contentTypeOf(document.fileName()));
    }

    static MediaType contentTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return TYPES_BY_EXTENSION.getOrDefault(
                fileName.substring(dot + 1).toLowerCase(Locale.ROOT), MediaType.APPLICATION_OCTET_STREAM);
    }
}
