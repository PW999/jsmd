package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ContentTextRepositoryIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    JdbcClient jdbc;
    ContentTextRepository repo;

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
        repo = new ContentTextRepository(jdbc);
    }

    private long insertContent(byte sha) {
        return jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 100) RETURNING id")
                .param(new byte[]{sha})
                .query(Long.class)
                .single();
    }

    private String storedText(long contentId) {
        return jdbc.sql("SELECT text FROM content_text WHERE content_id = ?")
                .param(contentId)
                .query(String.class)
                .single();
    }

    private String storedSource(long contentId) {
        return jdbc.sql("SELECT source FROM content_text WHERE content_id = ?")
                .param(contentId)
                .query(String.class)
                .single();
    }

    private int rowCount(long contentId) {
        return jdbc.sql("SELECT count(*) FROM content_text WHERE content_id = ?")
                .param(contentId)
                .query(Integer.class)
                .single();
    }

    @Test
    void upsertStoresText() {
        long cid = insertContent((byte) 1);
        repo.upsert(cid, "hello world", ExtractionResult.Source.TEXT_LAYER, 1, null);

        assertThat(storedText(cid)).isEqualTo("hello world");
        assertThat(storedSource(cid)).isEqualTo("TEXT_LAYER");
    }

    @Test
    void upsertStoresTextAndEmbedding() {
        long cid = insertContent((byte) 1);
        float[] embedding = new float[768];
        embedding[0] = 1.5f;
        embedding[1] = -0.3f;
        repo.upsert(cid, "test text", ExtractionResult.Source.TEXT_LAYER, 1, embedding);

        assertThat(storedText(cid)).isEqualTo("test text");
        assertThat(storedSource(cid)).isEqualTo("TEXT_LAYER");
        assertThat(jdbc.sql("SELECT vector_dims(embedding) FROM content_text WHERE content_id = ?")
                .param(cid).query(Integer.class).single()).isEqualTo(768);
        assertThat(jdbc.sql("SELECT embedding::text FROM content_text WHERE content_id = ?")
                .param(cid).query(String.class).single()).contains("[1.5,-0.3");
    }

    @Test
    void reprocessingReplacesExistingRow() {
        long cid = insertContent((byte) 2);
        repo.upsert(cid, "first", ExtractionResult.Source.TEXT_LAYER, 1, null);
        float[] embedding = new float[768];
        embedding[0] = 0f;
        repo.upsert(cid, "second", ExtractionResult.Source.OCR, 2, embedding);

        assertThat(rowCount(cid)).isEqualTo(1);
        assertThat(storedText(cid)).isEqualTo("second");
        assertThat(storedSource(cid)).isEqualTo("OCR");
        assertThat(jdbc.sql("SELECT vector_dims(embedding) FROM content_text WHERE content_id = ?")
                .param(cid).query(Integer.class).single()).isEqualTo(768);
        assertThat(jdbc.sql("SELECT embedding::text FROM content_text WHERE content_id = ?")
                .param(cid).query(String.class).single()).contains("[0,");
    }

    @Test
    void nulCharactersAreStripped() {
        long cid = insertContent((byte) 3);
        repo.upsert(cid, "hello\u0000world", ExtractionResult.Source.OCR, null, null);

        assertThat(storedText(cid)).isEqualTo("helloworld");
    }

    private int matches(String query) {
        return jdbc.sql("SELECT count(*) FROM content_text "
                        + "WHERE search_vector @@ websearch_to_tsquery('simple', ?)")
                .param(query)
                .query(Integer.class)
                .single();
    }

    @Test
    void replacedTextMatchesNewWordsAndNotOldWords() {
        long cid = insertContent((byte) 5);
        repo.upsert(cid, "apple banana", ExtractionResult.Source.TEXT_LAYER, 1, null);
        assertThat(matches("apple")).isEqualTo(1);

        repo.upsert(cid, "cherry", ExtractionResult.Source.OCR, 1, null);

        assertThat(matches("cherry")).isEqualTo(1);
        assertThat(matches("apple")).isZero();
        assertThat(matches("banana")).isZero();
    }

    @Test
    void textLongerThanTheIndexLimitIsStoredAndEarlyWordsAreFound() {
        long cid = insertContent((byte) 6);
        // distinct words, so an uncapped vector would exceed PostgreSQL's 1 MB tsvector limit
        StringBuilder sb = new StringBuilder("needle ");
        for (int i = 0; i < 400_000; i++) {
            sb.append("w").append(i).append(' ');
        }
        String text = sb.append("tailword").toString();

        repo.upsert(cid, text, ExtractionResult.Source.TEXT_LAYER, 1, null);

        assertThat(storedText(cid)).hasSize(text.length());
        assertThat(matches("needle")).isEqualTo(1);
        assertThat(matches("tailword")).isZero();
    }

    @Test
    void findSha256ReturnsRecordedDigest() {
        long cid = insertContent((byte) 4);

        assertThat(repo.findSha256(cid)).hasValue(new byte[]{4});
    }

    @Test
    void findSha256IsEmptyForUnknownContent() {
        assertThat(repo.findSha256(-1L)).isEmpty();
    }
}
