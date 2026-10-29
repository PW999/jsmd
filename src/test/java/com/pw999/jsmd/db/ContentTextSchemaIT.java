package com.pw999.jsmd.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ContentTextSchemaIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final AtomicInteger SHA_COUNTER = new AtomicInteger(1);

    private Connection migrate() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Insert a content row with a unique sha256 and return its id. */
    private long insertContent(Statement s) throws SQLException {
        // Use a counter-based unique sha256 per call to avoid duplicate key errors
        int n = SHA_COUNTER.getAndIncrement();
        String hex = String.format("%02x", n);
        try (ResultSet rs = s.executeQuery(
                "INSERT INTO content (sha256, size) VALUES ('\\x" + hex + "', 100) RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void tableExistsAndBasicInsertSucceeds() throws Exception {
        try (Connection c = migrate(); Statement s = c.createStatement()) {
            long contentId = insertContent(s);
            s.execute("INSERT INTO content_text (content_id, text, source) VALUES ("
                    + contentId + ", 'hello', 'TEXT_LAYER')");
            try (ResultSet rs = s.executeQuery(
                    "SELECT text, source FROM content_text WHERE content_id = " + contentId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("text")).isEqualTo("hello");
                assertThat(rs.getString("source")).isEqualTo("TEXT_LAYER");
            }
        }
    }

    @Test
    void primaryKeyRejectsDuplicateContentId() throws Exception {
        try (Connection c = migrate(); Statement s = c.createStatement()) {
            long contentId = insertContent(s);
            s.execute("INSERT INTO content_text (content_id, text, source) VALUES ("
                    + contentId + ", 'first', 'TEXT_LAYER')");
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO content_text (content_id, text, source) VALUES ("
                    + contentId + ", 'second', 'OCR')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("content_text_pkey");
        }
    }

    @Test
    void sourceCheckConstraintRejectsInvalidValue() throws Exception {
        try (Connection c = migrate(); Statement s = c.createStatement()) {
            long contentId = insertContent(s);
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO content_text (content_id, text, source) VALUES ("
                    + contentId + ", 'x', 'INVALID')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("content_text_source_check");
        }
    }

    @Test
    void searchVectorColumnAndGinIndexExist() throws Exception {
        try (Connection c = migrate(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("""
                    SELECT data_type, is_generated FROM information_schema.columns
                    WHERE table_name = 'content_text' AND column_name = 'search_vector'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualTo("tsvector");
                assertThat(rs.getString("is_generated")).isEqualTo("ALWAYS");
            }
            try (ResultSet rs = s.executeQuery("""
                    SELECT indexdef FROM pg_indexes
                    WHERE tablename = 'content_text' AND indexname = 'content_text_search_idx'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("indexdef")).contains("USING gin").contains("search_vector");
            }
        }
    }

    @Test
    void rowsStoredBeforeTheMigrationAreMatched() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("3")
                .load()
                .migrate();
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            long contentId = insertContent(s);
            s.execute("INSERT INTO content_text (content_id, text, source) VALUES ("
                    + contentId + ", 'An Old Invoice', 'OCR')");

            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .load()
                    .migrate();

            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM content_text "
                    + "WHERE search_vector @@ websearch_to_tsquery('simple', 'invoice')")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void allThreeSourceValuesAreAccepted() throws Exception {
        try (Connection c = migrate()) {
            for (String source : new String[]{"TEXT_LAYER", "OCR", "NONE"}) {
                long contentId;
                try (Statement s = c.createStatement()) {
                    contentId = insertContent(s);
                }
                try (Statement s = c.createStatement()) {
                    s.execute("INSERT INTO content_text (content_id, text, source) VALUES ("
                            + contentId + ", '', '" + source + "')");
                }
            }
        }
    }
}
