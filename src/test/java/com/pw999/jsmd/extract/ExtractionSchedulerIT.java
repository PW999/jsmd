package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pw999.jsmd.queue.QueueName;
import com.pw999.jsmd.queue.QueueProperties;
import com.pw999.jsmd.queue.WorkQueue;
import com.pw999.jsmd.scan.FileHasher;

/**
 * Integration test for {@link ExtractionScheduler} end-to-end: enqueue → drain → verify stored text.
 * Tests the scheduler's {@code drainQueue()} directly (without waiting for {@code @Scheduled} timer).
 */
@Testcontainers
class ExtractionSchedulerIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @org.junit.jupiter.api.io.TempDir
    Path tmp;

    JdbcClient jdbc;
    WorkQueue workQueue;
    ContentTextRepository repo;
    ExtractionScheduler scheduler;

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
        workQueue = new WorkQueue(jdbc, new QueueProperties(Duration.ZERO, 3, Duration.ofMinutes(15), Duration.ofDays(7), "0 30 3 * * *"));
        repo = new ContentTextRepository(jdbc);

        String library = System.getProperty("jsmd.tesseract.library", "libtesseract.so.5.5");
        ExtractProperties props = new ExtractProperties(
                true, Duration.ofSeconds(10), List.of("en"), null, library, 150);
        OcrService ocrService = new OcrService(props);
        PdfTextReader textReader = new PdfTextReader();
        PdfExtractor pdfExtractor = new PdfExtractor(textReader, ocrService);
        ImageExtractor imageExtractor = new ImageExtractor(ocrService);
        ExtractionService extractionService = new ExtractionService(workQueue, repo, pdfExtractor, imageExtractor, new FileHasher());

        scheduler = new ExtractionScheduler(props, extractionService);
    }

    private long enqueueTextPdf(byte sha, String text) throws Exception {
        Path file = tmp.resolve("doc_" + sha + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file.toFile());
        }
        long scanId = jdbc.sql("SELECT nextval('scan_id_seq')").query(Long.class).single();
        long contentId = jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, ?) RETURNING id")
                .param(new FileHasher().hash(file).sha256())
                .param(Files.size(file))
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)"
                + " VALUES (?, ?, ?, 0, ?)")
                .param(file.toAbsolutePath().toString())
                .param(contentId)
                .param(Files.size(file))
                .param(scanId)
                .update();
        workQueue.enqueue(QueueName.PROCESS, contentId);
        return contentId;
    }

    @Test
    void drainQueueProcessesPdfAndStoresText() throws Exception {
        long cid = enqueueTextPdf((byte) 1, "Scheduler integration test");

        int processed = scheduler.drainQueue();

        assertThat(processed).isGreaterThanOrEqualTo(1);
        String source = jdbc.sql("SELECT source FROM content_text WHERE content_id = ?")
                .param(cid).query(String.class).single();
        assertThat(source).isEqualTo("TEXT_LAYER");

        String status = jdbc.sql("SELECT status FROM work_item WHERE content_id = ?")
                .param(cid).query(String.class).single();
        assertThat(status).isEqualTo("DONE");
    }

    @Test
    void disabledSchedulerClaimsNothing() throws Exception {
        enqueueTextPdf((byte) 2, "Should not be processed");

        String library = System.getProperty("jsmd.tesseract.library", "libtesseract.so.5.5");
        ExtractProperties disabledProps = new ExtractProperties(
                false, Duration.ofSeconds(10), List.of("en"), null, library, 150);
        OcrService ocrService = new OcrService(disabledProps);
        PdfTextReader textReader = new PdfTextReader();
        ExtractionService svc = new ExtractionService(workQueue, repo,
                new PdfExtractor(textReader, ocrService),
                new ImageExtractor(ocrService),
                new FileHasher());
        ExtractionScheduler disabledScheduler = new ExtractionScheduler(disabledProps, svc);

        int processed = disabledScheduler.drainQueue();

        assertThat(processed).isZero();
        // Work item is still PENDING
        String status = jdbc.sql("SELECT status FROM work_item ORDER BY id DESC LIMIT 1")
                .query(String.class).single();
        assertThat(status).isEqualTo("PENDING");
    }
}
