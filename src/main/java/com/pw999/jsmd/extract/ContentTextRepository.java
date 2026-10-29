package com.pw999.jsmd.extract;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persists extracted text per content item.
 *
 * <p>Upsert semantics make reprocessing idempotent: a second store for the same content
 * replaces the existing row. NUL characters ({@code \u0000}) are stripped before storage
 * because Postgres rejects them in {@code text} columns.
 */
@Repository
public class ContentTextRepository {

    private final JdbcClient jdbc;

    public ContentTextRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores or replaces the extracted text for a content item.
     *
     * @param contentId  the content this text was extracted from
     * @param text       the extracted text (NUL characters are stripped automatically)
     * @param source     how the text was obtained
     * @param pageCount  number of pages (may be null for images)
     */
    public void upsert(long contentId, String text, ExtractionResult.Source source, Integer pageCount) {
        String sanitised = text == null ? "" : text.replace("\u0000", "");
        jdbc.sql("""
                        INSERT INTO content_text (content_id, text, source, page_count, extracted_at)
                        VALUES (?, ?, ?, ?, now())
                        ON CONFLICT (content_id) DO UPDATE
                            SET text = EXCLUDED.text,
                                source = EXCLUDED.source,
                                page_count = EXCLUDED.page_count,
                                extracted_at = now()
                        """)
                .param(contentId)
                .param(sanitised)
                .param(source.name())
                .param(pageCount)
                .update();
    }

    /**
     * Returns the known file paths for the given content, ordered by row id (insertion order).
     *
     * @param contentId the content whose paths are needed
     * @return list of absolute paths, may be empty
     */
    public List<String> findFilePaths(long contentId) {
        return jdbc.sql("SELECT path FROM file_location WHERE content_id = ? ORDER BY id")
                .param(contentId)
                .query(String.class)
                .list();
    }

    /**
     * Returns the SHA-256 recorded for the given content.
     *
     * @param contentId the content whose hash is needed
     * @return the raw 32-byte digest, or empty if the content does not exist
     */
    public Optional<byte[]> findSha256(long contentId) {
        return jdbc.sql("SELECT sha256 FROM content WHERE id = ?")
                .param(contentId)
                .query(byte[].class)
                .optional();
    }
}
