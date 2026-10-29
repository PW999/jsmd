package com.pw999.jsmd.document;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read side of the file index: lists known paths and loads one with its extracted text.
 * Extraction state is derived from a {@code LEFT JOIN} on {@code content_text}.
 */
@Repository
public class DocumentRepository {

    private static final String HEADLINE_OPTIONS = "StartSel=" + Snippet.START + ", StopSel=" + Snippet.STOP
            + ", MaxFragments=2, MaxWords=30, MinWords=10";

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns one page of rows ordered by path; the text column is never selected. */
    public List<DocumentRow> list(int page, int size) {
        return jdbc.sql("""
                        SELECT fl.id, fl.path, fl.size, ct.source
                        FROM file_location fl
                        LEFT JOIN content_text ct ON ct.content_id = fl.content_id
                        ORDER BY fl.path, fl.id
                        LIMIT ? OFFSET ?
                        """)
                .param(size)
                .param((long) page * size)
                .query((rs, n) -> new DocumentRow(
                        rs.getLong("id"),
                        rs.getString("path"),
                        rs.getLong("size"),
                        ExtractionState.of(rs.getString("source"))))
                .list();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM file_location").query(Long.class).single();
    }

    /**
     * Returns one page of paths whose extracted text matches the query, best match first.
     * The query is a {@code websearch_to_tsquery} string (words, {@code "phrases"}, {@code or}, {@code -word}) and
     * is only ever a bind parameter. The headline is computed for the page only.
     */
    public List<SearchResult> search(String query, int page, int size) {
        return jdbc.sql("""
                        SELECT m.id, m.path, m.size,
                               ts_headline('simple', translate(left(ct.text, 500000), ?, ?), websearch_to_tsquery('simple', ?),
                                           ?) AS snippet
                        FROM (
                            SELECT fl.id, fl.path, fl.size, fl.content_id,
                                   ts_rank_cd(ct.search_vector, q.query) AS rank
                            FROM file_location fl
                            JOIN content_text ct ON ct.content_id = fl.content_id
                            CROSS JOIN websearch_to_tsquery('simple', ?) AS q(query)
                            WHERE ct.search_vector @@ q.query
                            ORDER BY rank DESC, fl.path, fl.id
                            LIMIT ? OFFSET ?
                        ) m
                        JOIN content_text ct ON ct.content_id = m.content_id
                        ORDER BY m.rank DESC, m.path, m.id
                        """)
                .param(Snippet.MASKED)
                .param(Snippet.MASKS)
                .param(query)
                .param(HEADLINE_OPTIONS)
                .param(query)
                .param(size)
                .param((long) page * size)
                .query((rs, n) -> new SearchResult(
                        rs.getLong("id"),
                        rs.getString("path"),
                        rs.getLong("size"),
                        Snippet.parse(rs.getString("snippet"))))
                .list();
    }

    public long countMatches(String query) {
        return jdbc.sql("""
                        SELECT count(*)
                        FROM file_location fl
                        JOIN content_text ct ON ct.content_id = fl.content_id
                        WHERE ct.search_vector @@ websearch_to_tsquery('simple', ?)
                        """)
                .param(query)
                .query(Long.class)
                .single();
    }

    public Optional<DocumentDetail> find(long id) {
        return jdbc.sql("""
                        SELECT fl.id, fl.path, fl.size, c.sha256, ct.source, ct.text, ct.page_count
                        FROM file_location fl
                        JOIN content c ON c.id = fl.content_id
                        LEFT JOIN content_text ct ON ct.content_id = fl.content_id
                        WHERE fl.id = ?
                        """)
                .param(id)
                .query((rs, n) -> new DocumentDetail(
                        rs.getLong("id"),
                        rs.getString("path"),
                        rs.getLong("size"),
                        ExtractionState.of(rs.getString("source")),
                        rs.getString("text"),
                        rs.getObject("page_count", Integer.class),
                        rs.getBytes("sha256")))
                .optional();
    }
}
