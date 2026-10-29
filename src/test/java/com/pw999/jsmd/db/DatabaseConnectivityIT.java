package com.pw999.jsmd.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pw999.jsmd.JsmdApplication;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DatabaseConnectivityIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private SpringApplicationBuilder app(String jdbcUrl, String user, String password) {
        return new SpringApplicationBuilder(JsmdApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "JSMD_DB_URL=" + jdbcUrl,
                        "JSMD_DB_USER=" + user,
                        "JSMD_DB_PASSWORD=" + password,
                        "jsmd.admin.username=admin",
                        "jsmd.admin.password=test-password");
    }

    @Test
    void healthReportsUpWithLiveDatabase() throws Exception {
        try (ConfigurableApplicationContext ctx = app(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).run()) {
            int port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
            assertThat(response.body()).doesNotContain("components").doesNotContain("postgres");

            HttpResponse<String> protectedPage = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/documents")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(protectedPage.statusCode()).isEqualTo(302);
            assertThat(protectedPage.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login"));
        }
    }

    @Test
    void startupFailsWithDatabaseErrorWhenUnreachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String url = "jdbc:postgresql://localhost:" + closedPort + "/jsmd?connectTimeout=2";

        assertThatThrownBy(() -> app(url, "jsmd", "jsmd").run().close())
                .hasRootCauseInstanceOf(java.io.IOException.class)
                .satisfies(e -> assertThat(causeChain(e)).anyMatch(SQLException.class::isInstance));
    }

    private static java.util.List<Throwable> causeChain(Throwable t) {
        java.util.List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable c = t; c != null && !chain.contains(c); c = c.getCause()) {
            chain.add(c);
        }
        return chain;
    }
}
