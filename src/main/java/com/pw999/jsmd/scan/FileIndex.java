package com.pw999.jsmd.scan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The persistent index of known files (locations) and the distinct contents they point at. */
@Repository
public class FileIndex {

    /** A known file. {@code sha256} is the hash of the content it currently points at. */
    public record Location(long id, String path, long contentId, long size, long modifiedAtMs, byte[] sha256) {
    }

    private final JdbcClient jdbc;

    public FileIndex(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long nextScanId() {
        return jdbc.sql("SELECT nextval('scan_id_seq')").query(Long.class).single();
    }

    public Map<String, Location> findByPaths(List<String> paths) {
        Map<String, Location> found = new HashMap<>();
        jdbc.sql("""
                        SELECT l.id, l.path, l.content_id, l.size, l.modified_at_ms, c.sha256
                        FROM file_location l JOIN content c ON c.id = l.content_id
                        WHERE l.path = ANY (?)
                        """)
                .param(paths.toArray(String[]::new))
                .query((rs, rowNum) -> new Location(rs.getLong("id"), rs.getString("path"), rs.getLong("content_id"),
                        rs.getLong("size"), rs.getLong("modified_at_ms"), rs.getBytes("sha256")))
                .list()
                .forEach(l -> found.put(l.path(), l));
        return found;
    }

    public void markSeen(List<Long> locationIds, long scanId) {
        if (locationIds.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE file_location SET last_seen_scan_id = ? WHERE id = ANY (?)")
                .param(scanId)
                .param(locationIds.stream().mapToLong(Long::longValue).toArray())
                .update();
    }

    /** Marks the file at this path, and everything below it, as seen without otherwise touching it. */
    public int markKept(String path, long scanId) {
        return jdbc.sql("""
                        UPDATE file_location SET last_seen_scan_id = ?
                        WHERE path = ? OR starts_with(path, ?)
                        """)
                .param(scanId)
                .param(path)
                .param(path.endsWith("/") ? path : path + "/")
                .update();
    }

    public Optional<Long> findContentId(byte[] sha256) {
        return jdbc.sql("SELECT id FROM content WHERE sha256 = ?").param(sha256).query(Long.class).optional();
    }

    public long insertContent(byte[] sha256, long size) {
        return jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, ?) RETURNING id")
                .param(sha256)
                .param(size)
                .query(Long.class)
                .single();
    }

    public void insertLocation(String path, long contentId, long size, long modifiedAtMs, long scanId) {
        jdbc.sql("""
                        INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .param(path)
                .param(contentId)
                .param(size)
                .param(modifiedAtMs)
                .param(scanId)
                .update();
    }

    /** Same content, new size and modification time (the file was touched but is identical). */
    public void updateStat(long locationId, long size, long modifiedAtMs) {
        jdbc.sql("UPDATE file_location SET size = ?, modified_at_ms = ? WHERE id = ?")
                .param(size)
                .param(modifiedAtMs)
                .param(locationId)
                .update();
    }

    /** The file at this location now has different content. */
    public void repoint(long locationId, long contentId, long size, long modifiedAtMs) {
        jdbc.sql("UPDATE file_location SET content_id = ?, size = ?, modified_at_ms = ? WHERE id = ?")
                .param(contentId)
                .param(size)
                .param(modifiedAtMs)
                .param(locationId)
                .update();
    }

    /**
     * A location of this content that was not seen in this scan although it lies in one of the fully walked roots,
     * so its file is gone.
     */
    public Optional<Long> findMissingLocation(long contentId, long scanId, List<String> rootPrefixes) {
        return jdbc.sql("""
                        SELECT id FROM file_location
                        WHERE content_id = ? AND last_seen_scan_id < ?
                          AND EXISTS (SELECT 1 FROM unnest(?::text[]) r WHERE starts_with(path, r))
                        ORDER BY id
                        LIMIT 1
                        """)
                .param(contentId)
                .param(scanId)
                .param(rootPrefixes.toArray(String[]::new))
                .query(Long.class)
                .optional();
    }

    /** The file known at an earlier path now lives at this path. */
    public void moveLocation(long locationId, String newPath, long size, long modifiedAtMs, long scanId) {
        jdbc.sql("""
                        UPDATE file_location
                        SET path = ?, size = ?, modified_at_ms = ?, last_seen_scan_id = ?
                        WHERE id = ?
                        """)
                .param(newPath)
                .param(size)
                .param(modifiedAtMs)
                .param(scanId)
                .param(locationId)
                .update();
    }

    /** Deletes locations in the given roots that were not seen in this scan; returns their content ids. */
    public List<Long> deleteUnseen(long scanId, List<String> rootPrefixes) {
        return jdbc.sql("""
                        DELETE FROM file_location
                        WHERE last_seen_scan_id < ?
                          AND EXISTS (SELECT 1 FROM unnest(?::text[]) r WHERE starts_with(path, r))
                        RETURNING content_id
                        """)
                .param(scanId)
                .param(rootPrefixes.toArray(String[]::new))
                .query(Long.class)
                .list();
    }

    public int countLocations(long contentId) {
        return jdbc.sql("SELECT count(*) FROM file_location WHERE content_id = ?")
                .param(contentId)
                .query(Integer.class)
                .single();
    }
}
