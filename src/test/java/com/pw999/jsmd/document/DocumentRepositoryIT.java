package com.pw999.jsmd.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DocumentRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    JdbcClient jdbc;
    DocumentRepository repo;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        repo = new DocumentRepository(jdbc);
    }

    private long insertContent(int sha) {
        return jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 100) RETURNING id")
                .param(new byte[]{(byte) sha})
                .query(Long.class)
                .single();
    }

    private long insertLocation(String path, long contentId) {
        return jdbc.sql("""
                        INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)
                        VALUES (?, ?, 100, 0, 1) RETURNING id
                        """)
                .param(path)
                .param(contentId)
                .query(Long.class)
                .single();
    }

    private void insertText(long contentId, String text, String source, Integer pages) {
        jdbc.sql("INSERT INTO content_text (content_id, text, source, page_count) VALUES (?, ?, ?, ?)")
                .param(contentId).param(text).param(source).param(pages).update();
    }

    @Test
    void listIsOrderedByPathAndPaged() {
        long c = insertContent(1);
        insertLocation("/b.pdf", c);
        insertLocation("/c.pdf", c);
        insertLocation("/a.pdf", c);

        assertThat(repo.count()).isEqualTo(3);
        assertThat(repo.list(0, 2)).extracting(DocumentRow::path).containsExactly("/a.pdf", "/b.pdf");
        assertThat(repo.list(1, 2)).extracting(DocumentRow::path).containsExactly("/c.pdf");
        assertThat(repo.list(2, 2)).isEmpty();
    }

    @Test
    void emptyIndexGivesEmptyList() {
        assertThat(repo.count()).isZero();
        assertThat(repo.list(0, 50)).isEmpty();
    }

    @Test
    void stateIsPendingUntilTextIsStoredThenFollowsSource() {
        long pending = insertContent(1);
        long layer = insertContent(2);
        long ocr = insertContent(3);
        long none = insertContent(4);
        insertLocation("/1.pdf", pending);
        insertLocation("/2.pdf", layer);
        insertLocation("/3.pdf", ocr);
        insertLocation("/4.pdf", none);
        insertText(layer, "t", "TEXT_LAYER", 1);
        insertText(ocr, "t", "OCR", 1);
        insertText(none, "", "NONE", 1);

        List<DocumentRow> rows = repo.list(0, 50);

        assertThat(rows).extracting(DocumentRow::state).containsExactly(
                ExtractionState.PENDING, ExtractionState.TEXT_LAYER, ExtractionState.OCR, ExtractionState.NONE);
    }

    @Test
    void identicalContentAtTwoPathsGivesTwoRows() {
        long c = insertContent(1);
        insertText(c, "same", "TEXT_LAYER", 1);
        insertLocation("/x/a.pdf", c);
        insertLocation("/y/a.pdf", c);

        assertThat(repo.list(0, 50)).extracting(DocumentRow::path).containsExactly("/x/a.pdf", "/y/a.pdf");
    }

    @Test
    void findReturnsTextPagesAndHash() {
        long c = insertContent(7);
        insertText(c, "hello\nworld", "OCR", 3);
        long id = insertLocation("/docs/scan.pdf", c);

        DocumentDetail d = repo.find(id).orElseThrow();

        assertThat(d.path()).isEqualTo("/docs/scan.pdf");
        assertThat(d.fileName()).isEqualTo("scan.pdf");
        assertThat(d.state()).isEqualTo(ExtractionState.OCR);
        assertThat(d.text()).isEqualTo("hello\nworld");
        assertThat(d.pageCount()).isEqualTo(3);
        assertThat(d.sha256()).containsExactly(7);
    }

    @Test
    void findOfPendingFileHasNoText() {
        long id = insertLocation("/p.pdf", insertContent(1));

        DocumentDetail d = repo.find(id).orElseThrow();

        assertThat(d.state()).isEqualTo(ExtractionState.PENDING);
        assertThat(d.text()).isNull();
        assertThat(d.pageCount()).isNull();
        assertThat(d.hasText()).isFalse();
    }

    private long indexed(String path, int sha, String text) {
        long c = insertContent(sha);
        insertText(c, text, "TEXT_LAYER", 1);
        return insertLocation(path, c);
    }

    private List<String> searchPaths(String query) {
        return repo.search(query, 0, 50).stream().map(SearchResult::path).toList();
    }

    @Test
    void searchMatchesSingleWordCaseInsensitively() {
        indexed("/a.pdf", 1, "Please pay this INVOICE today");
        indexed("/b.pdf", 2, "nothing relevant");

        assertThat(searchPaths("invoice")).containsExactly("/a.pdf");
        assertThat(searchPaths("Invoice")).containsExactly("/a.pdf");
        assertThat(repo.countMatches("invoice")).isEqualTo(1);
    }

    @Test
    void searchRequiresAllWords() {
        indexed("/both.pdf", 1, "a red bicycle");
        indexed("/one.pdf", 2, "a red car");

        assertThat(searchPaths("red bicycle")).containsExactly("/both.pdf");
    }

    @Test
    void searchPhraseNeedsAdjacentWordsInOrder() {
        indexed("/adjacent.pdf", 1, "my red bicycle is old");
        indexed("/apart.pdf", 2, "a bicycle that is red");
        indexed("/reversed.pdf", 3, "bicycle red");

        assertThat(searchPaths("\"red bicycle\"")).containsExactly("/adjacent.pdf");
    }

    @Test
    void searchSupportsOrAndExclusion() {
        indexed("/cat.pdf", 1, "a cat");
        indexed("/dog.pdf", 2, "a dog");
        indexed("/catfish.pdf", 3, "a cat and a fish");
        indexed("/none.pdf", 4, "a bird");

        assertThat(searchPaths("cat or dog")).containsExactly("/cat.pdf", "/catfish.pdf", "/dog.pdf");
        assertThat(searchPaths("cat -fish")).containsExactly("/cat.pdf");
        assertThat(repo.countMatches("cat -fish")).isEqualTo(1);
    }

    @Test
    void searchRanksMoreFrequentMatchesFirst() {
        indexed("/a-once.pdf", 1, "invoice and a lot of other words around it");
        indexed("/z-often.pdf", 2, "invoice invoice invoice");

        assertThat(searchPaths("invoice")).containsExactly("/z-often.pdf", "/a-once.pdf");
    }

    @Test
    void searchTiesAreOrderedByPath() {
        indexed("/b.pdf", 1, "same words");
        indexed("/a.pdf", 2, "same words");

        assertThat(searchPaths("same")).containsExactly("/a.pdf", "/b.pdf");
    }

    @Test
    void searchGivesOneRowPerPathForIdenticalContent() {
        long c = insertContent(1);
        insertText(c, "shared words", "TEXT_LAYER", 1);
        insertLocation("/x/a.pdf", c);
        insertLocation("/y/a.pdf", c);

        assertThat(searchPaths("shared")).containsExactly("/x/a.pdf", "/y/a.pdf");
        assertThat(repo.countMatches("shared")).isEqualTo(2);
    }

    @Test
    void searchIgnoresFilesWithoutUsableText() {
        insertLocation("/pending.pdf", insertContent(1));
        long none = insertContent(2);
        insertText(none, "", "NONE", 1);
        insertLocation("/none.pdf", none);

        assertThat(searchPaths("anything")).isEmpty();
        assertThat(repo.countMatches("anything")).isZero();
    }

    @Test
    void searchPagesResultsAndCountsAll() {
        for (int i = 0; i < 5; i++) {
            indexed("/f" + i + ".pdf", i + 1, "common word");
        }

        assertThat(repo.countMatches("common")).isEqualTo(5);
        assertThat(repo.search("common", 0, 2)).extracting(SearchResult::path).containsExactly("/f0.pdf", "/f1.pdf");
        assertThat(repo.search("common", 1, 2)).extracting(SearchResult::path).containsExactly("/f2.pdf", "/f3.pdf");
        assertThat(repo.search("common", 2, 2)).extracting(SearchResult::path).containsExactly("/f4.pdf");
        assertThat(repo.search("common", 3, 2)).isEmpty();
    }

    @Test
    void searchReturnsSnippetWithMatchedWordsHighlighted() {
        indexed("/a.pdf", 1, "Before the Invoice <b>after</b> &amp; <<x>>");

        SearchResult result = repo.search("invoice", 0, 50).getFirst();

        assertThat(result.snippet().segments())
                .anySatisfy(s -> assertThat(s).isEqualTo(new Snippet.Segment("Invoice", true)));
        assertThat(result.snippet().segments().stream().filter(Snippet.Segment::highlighted)).hasSize(1);
        assertThat(result.snippet().segments().stream().map(Snippet.Segment::text).reduce("", String::concat))
                .startsWith("Before the Invoice <b>after")
                .doesNotContain("\uE002", "\uE003", "\uE004");
    }

    @Test
    void malformedQueriesReturnNothingWithoutError() {
        indexed("/a.pdf", 1, "plain words");

        for (String query : List.of("\"", "& |", "'; DROP TABLE content_text; --", "(((", "!!!")) {
            assertThat(repo.search(query, 0, 50)).as(query).isEmpty();
            assertThat(repo.countMatches(query)).as(query).isZero();
        }
        assertThat(searchPaths("plain")).containsExactly("/a.pdf");
    }

    @Test
    void findIsEmptyForUnknownId() {
        assertThat(repo.find(-1L)).isEmpty();
    }
}
