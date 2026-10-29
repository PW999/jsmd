package com.pw999.jsmd.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pw999.jsmd.JsmdApplication;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the running application over HTTP, including the error page that MockMvc cannot render.
 */
@Testcontainers
class ApplicationFlowIT {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" type=\"hidden\" value=\"([^\"]+)\"|name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static ConfigurableApplicationContext context;
    static String base;

    HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @BeforeAll
    static void start() {
        context = new SpringApplicationBuilder(JsmdApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "JSMD_DB_URL=" + POSTGRES.getJdbcUrl(),
                        "JSMD_DB_USER=" + POSTGRES.getUsername(),
                        "JSMD_DB_PASSWORD=" + POSTGRES.getPassword(),
                        "jsmd.admin.username=admin",
                        "jsmd.admin.password=flow-test-pw")
                .run();
        base = "http://localhost:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String csrf = csrfToken(get("/login").body());
        String form = "username=" + enc(username) + "&password=" + enc(password) + "&_csrf=" + enc(csrf);
        return client.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfToken(String html) {
        Matcher m = CSRF.matcher(html);
        assertThat(m.find()).as("csrf token in page").isTrue();
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    void unknownPathShowsNotFoundInsideLayoutForAuthenticatedUser() throws Exception {
        HttpResponse<String> login = login("admin", "flow-test-pw");
        assertThat(login.statusCode()).isEqualTo(302);

        HttpResponse<String> response = get("/no/such/page");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("Page not found")
                .contains("<header class=\"banner\">")
                .contains("<nav class=\"menu\"")
                .contains("<span class=\"username\">admin</span>");
    }

    @Test
    void loginShowsLandingPageAndLogoutReturnsToLogin() throws Exception {
        HttpResponse<String> login = login("admin", "flow-test-pw");
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/"));

        HttpResponse<String> home = get("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("<header class=\"banner\">").contains("Welcome");

        String csrf = csrfToken(home.body());
        HttpResponse<String> logout = client.send(HttpRequest.newBuilder(URI.create(base + "/logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf=" + enc(csrf))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(logout.statusCode()).isEqualTo(302);
        assertThat(logout.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login?logout"));

        HttpResponse<String> after = get("/");
        assertThat(after.statusCode()).isEqualTo(302);
        assertThat(after.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login"));
    }

    @Test
    void wrongCredentialsShowGenericErrorOnLoginPage() throws Exception {
        HttpResponse<String> login = login("admin", "wrong");
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login?error"));

        HttpResponse<String> page = get("/login?error");
        assertThat(page.body()).contains("Invalid username or password.");
        assertThat(get("/").statusCode()).isEqualTo(302);
    }

    @Test
    void documentsListAndDetailRenderRealRowsAndDownloadServesTheFile() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("flow", ".pdf");
        byte[] bytes = "flow-bytes".getBytes(StandardCharsets.UTF_8);
        java.nio.file.Files.write(file, bytes);
        try {
            var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
            byte[] sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            long contentId = jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, ?) RETURNING id")
                    .param(sha).param((long) bytes.length).query(Long.class).single();
            long locationId = jdbc.sql("""
                            INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)
                            VALUES (?, ?, ?, 0, 1) RETURNING id
                            """)
                    .param(file.toString()).param(contentId).param((long) bytes.length)
                    .query(Long.class).single();
            jdbc.sql("INSERT INTO content_text (content_id, text, source, page_count) VALUES (?, 'a < b', 'OCR', 1)")
                    .param(contentId).update();
            assertThat(login("admin", "flow-test-pw").statusCode()).isEqualTo(302);

            HttpResponse<String> list = get("/documents");
            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(list.body()).contains(file.getFileName().toString()).contains("/documents/" + locationId);

            HttpResponse<String> detail = get("/documents/" + locationId);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(detail.body()).contains("a &lt; b");

            HttpResponse<byte[]> download = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/documents/" + locationId + "/download")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.body()).isEqualTo(bytes);

            java.nio.file.Files.write(file, "tampered".getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> changed = get("/documents/" + locationId + "/download");
            assertThat(changed.statusCode()).isEqualTo(409);
            assertThat(changed.body()).contains("changed since it was indexed").contains("<header class=\"banner\">");

            assertThat(get("/documents/999999").statusCode()).isEqualTo(404);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void searchFindsStoredTextAndRequiresLogin() throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        byte[] sha = java.security.MessageDigest.getInstance("SHA-256").digest("search-flow".getBytes(StandardCharsets.UTF_8));
        long contentId = jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 11) RETURNING id")
                .param(sha).query(Long.class).single();
        long locationId = jdbc.sql("""
                        INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)
                        VALUES ('/flow/searchable-report.pdf', ?, 11, 0, 1) RETURNING id
                        """)
                .param(contentId).query(Long.class).single();
        jdbc.sql("INSERT INTO content_text (content_id, text, source, page_count) "
                        + "VALUES (?, 'The quarterly Zebrafinch report <b>draft</b>', 'TEXT_LAYER', 1)")
                .param(contentId).update();

        HttpResponse<String> anonymous = get("/documents?q=zebrafinch");
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(anonymous.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login"));
        assertThat(anonymous.body()).doesNotContain("searchable-report");

        assertThat(login("admin", "flow-test-pw").statusCode()).isEqualTo(302);

        HttpResponse<String> found = get("/documents?q=" + enc("ZEBRAFINCH report"));
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body())
                .contains("/documents/" + locationId)
                .contains("searchable-report.pdf")
                .contains("<mark>Zebrafinch</mark>")
                .contains("&lt;b&gt;draft")
                .contains("1 file matches");

        HttpResponse<String> none = get("/documents?q=" + enc("zebrafinch -report"));
        assertThat(none.statusCode()).isEqualTo(200);
        assertThat(none.body()).contains("No documents match").doesNotContain("searchable-report");

        assertThat(get("/documents?q=" + enc("\"")).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> post(String path, String csrf) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(csrf == null ? "" : "_csrf=" + enc(csrf))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void queuePageListsItemsAndRemovesFailedOnesOnly() throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class);
        jdbc.sql("DELETE FROM work_item").update();
        long failedContent = jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 1) RETURNING id")
                .param(new byte[]{101}).query(Long.class).single();
        long pendingContent = jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 1) RETURNING id")
                .param(new byte[]{102}).query(Long.class).single();
        long otherFailedContent = jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 1) RETURNING id")
                .param(new byte[]{103}).query(Long.class).single();
        long failed = jdbc.sql("INSERT INTO work_item (queue, content_id, status, attempts, last_error) "
                        + "VALUES ('PROCESS', ?, 'FAILED', 5, 'ocr <failed>') RETURNING id")
                .param(failedContent).query(Long.class).single();
        long pending = jdbc.sql("INSERT INTO work_item (queue, content_id, status) VALUES ('PROCESS', ?, 'PENDING') RETURNING id")
                .param(pendingContent).query(Long.class).single();
        jdbc.sql("INSERT INTO work_item (queue, content_id, status) VALUES ('DELETE', ?, 'FAILED')")
                .param(otherFailedContent).update();

        HttpResponse<String> anonymous = get("/queue");
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(anonymous.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login"));

        assertThat(login("admin", "flow-test-pw").statusCode()).isEqualTo(302);
        HttpResponse<String> page = get("/queue");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("data-refresh-seconds=\"5\"")
                .doesNotContain("http-equiv=\"refresh\"")
                .contains("ocr &lt;failed&gt;")
                .contains("/queue/items/" + failed + "/remove")
                .doesNotContain("/queue/items/" + pending + "/remove")
                .contains("/queue/purge");
        String csrf = csrfToken(page.body());

        assertThat(post("/queue/items/" + failed + "/remove", null).statusCode()).isEqualTo(403);
        assertThat(jdbc.sql("SELECT count(*) FROM work_item").query(Long.class).single()).isEqualTo(3);

        HttpResponse<String> removed = post("/queue/items/" + failed + "/remove", csrf);
        assertThat(removed.statusCode()).isEqualTo(302);
        assertThat(removed.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/queue"));
        assertThat(get("/queue").body()).contains("Item " + failed + " removed.");
        assertThat(jdbc.sql("SELECT count(*) FROM work_item WHERE id = ?").param(failed).query(Long.class).single()).isZero();

        post("/queue/items/" + pending + "/remove", csrf);
        assertThat(get("/queue").body()).contains("it is not failed");
        assertThat(jdbc.sql("SELECT count(*) FROM work_item WHERE id = ?").param(pending).query(Long.class).single())
                .isEqualTo(1);

        assertThat(post("/queue/purge", csrf).statusCode()).isEqualTo(302);
        assertThat(get("/queue").body()).contains("Purged 1 finished item(s).");
        assertThat(jdbc.sql("SELECT count(*) FROM work_item WHERE status = 'FAILED'").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM work_item WHERE status = 'PENDING'").query(Long.class).single())
                .isEqualTo(1);
    }
}
