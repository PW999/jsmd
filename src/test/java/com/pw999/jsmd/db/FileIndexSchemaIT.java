package com.pw999.jsmd.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FileIndexSchemaIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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

    @Test
    void migrationAppliesOnEmptyDatabase() throws Exception {
        try (Connection c = migrate(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO content (sha256, size) VALUES ('\\x00', 1)");
            s.execute("INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)"
                    + " SELECT '/a', id, 1, 0, 1 FROM content");
            s.execute("INSERT INTO work_item (queue, content_id, status) SELECT 'PROCESS', id, 'PENDING' FROM content");
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void uniqueIndexRejectsSecondOpenItemForSameQueueAndContent() throws Exception {
        try (Connection c = migrate(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO content (sha256, size) VALUES ('\\x01', 1)");
            s.execute("INSERT INTO work_item (queue, content_id, status) SELECT 'PROCESS', id, 'PENDING' FROM content");

            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO work_item (queue, content_id, status) SELECT 'PROCESS', id, 'CLAIMED' FROM content"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("work_item_open_uq");

            // another queue and closed items are unaffected
            s.execute("INSERT INTO work_item (queue, content_id, status) SELECT 'DELETE', id, 'PENDING' FROM content");
            s.execute("INSERT INTO work_item (queue, content_id, status) SELECT 'PROCESS', id, 'DONE' FROM content");
        }
    }
}
