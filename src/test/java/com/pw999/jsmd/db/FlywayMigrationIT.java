package com.pw999.jsmd.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.pw999.jsmd.JsmdApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FlywayMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(JsmdApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "JSMD_DB_URL=" + POSTGRES.getJdbcUrl(),
                        "JSMD_DB_USER=" + POSTGRES.getUsername(),
                        "JSMD_DB_PASSWORD=" + POSTGRES.getPassword(),
                        "jsmd.admin.username=admin",
                        "jsmd.admin.password=test-password")
                .run();
    }

    private record History(int rows, Timestamp installedOn) {
    }

    private History history() throws Exception {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT count(*), max(installed_on) FROM flyway_schema_history WHERE success")) {
            rs.next();
            return new History(rs.getInt(1), rs.getTimestamp(2));
        }
    }

    @Test
    void appliesMigrationsOnEmptyDatabaseAndDoesNotReapplyOnRestart() throws Exception {
        try (ConfigurableApplicationContext ignored = start()) {
            // first start applied the migrations
        }
        History first = history();
        assertThat(first.rows()).isEqualTo(4);

        try (ConfigurableApplicationContext ignored = start()) {
            // restart against an up-to-date database
        }
        History second = history();
        assertThat(second.rows()).isEqualTo(first.rows());
        assertThat(second.installedOn()).isEqualTo(first.installedOn());
    }
}
