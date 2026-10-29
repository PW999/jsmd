package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * Integration test for {@link ExtractionService}.
 * Uses a real Postgres container, real files on disk, and real extractors.
 * OCR-dependent tests are skipped when Tesseract is not installed.
 */
@Testcontainers
class ExtractionServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final String LIBRARY = System.getProperty("jsmd.tesseract.library", "libtesseract.so.5.5");
    private static boolean tesseractAvailable;

    @TempDir
    Path tmp;

    JdbcClient jdbc;
    WorkQueue workQueue;
    ContentTextRepository repo;
    ExtractionService service;

    @BeforeEach
    void setUp() {
        // Check Tesseract availability
        tesseractAvailable = isTesseractAvailable();

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

        ExtractProperties props = new ExtractProperties(
                true, Duration.ofSeconds(10), List.of("en"), null, LIBRARY, 150);
        OcrService ocrService = new OcrService(props);
        PdfTextReader textReader = new PdfTextReader();
        PdfExtractor pdfExtractor = new PdfExtractor(textReader, ocrService);
        ImageExtractor imageExtractor = new ImageExtractor(ocrService);

        service = new ExtractionService(workQueue, repo, pdfExtractor, imageExtractor, new FileHasher());
    }

    private static boolean isTesseractAvailable() {
        try (TesseractOcrEngine e = new TesseractOcrEngine(LIBRARY, null, "eng")) {
            return true;
        } catch (OcrException ignored) {
            return false;
        }
    }

    // --- helpers ---

    private final FileHasher hasher = new FileHasher();

    /** Inserts a content row with the file's real SHA-256 and a file_location pointing at it, then enqueues it. */
    private long setupContent(Path file) throws IOException {
        byte[] sha = Files.exists(file) ? hasher.hash(file).sha256() : new byte[32];
        long contentId = insertContent(sha, Files.exists(file) ? Files.size(file) : 0L);
        addLocation(contentId, file);
        workQueue.enqueue(QueueName.PROCESS, contentId);
        return contentId;
    }

    private long insertContent(byte[] sha, long size) {
        return jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, ?) RETURNING id")
                .param(sha)
                .param(size)
                .query(Long.class)
                .single();
    }

    private void addLocation(long contentId, Path file) {
        long scanId = jdbc.sql("SELECT nextval('scan_id_seq')").query(Long.class).single();
        jdbc.sql("INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id)"
                + " VALUES (?, ?, 0, 0, ?)")
                .param(file.toAbsolutePath().toString())
                .param(contentId)
                .param(scanId)
                .update();
    }

    private Path textPdf(String text) throws Exception {
        Path file = tmp.resolve("text_" + text.hashCode() + ".pdf");
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
        return file;
    }

    private Path blankPdf() throws Exception {
        Path file = tmp.resolve("blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(file.toFile());
        }
        return file;
    }

    private Path solidPng() throws Exception {
        Path file = tmp.resolve("image.png");
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.PLAIN, 24));
        g.drawString("OCR", 60, 60);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    private String storedSource(long contentId) {
        return jdbc.sql("SELECT source FROM content_text WHERE content_id = ?")
                .param(contentId)
                .query(String.class)
                .single();
    }

    private String workItemStatus(long contentId) {
        return jdbc.sql("SELECT status FROM work_item WHERE content_id = ? ORDER BY id DESC LIMIT 1")
                .param(contentId)
                .query(String.class)
                .single();
    }

    // --- tests ---

    @Test
    void successfulTextPdfIsStoredAndCompleted() throws Exception {
        long cid = setupContent(textPdf("Hello integration test"));

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(storedSource(cid)).isEqualTo("TEXT_LAYER");
        assertThat(workItemStatus(cid)).isEqualTo("DONE");
    }

    @Test
    void noReadablePathFailsItem() throws Exception {
        Path nonexistent = tmp.resolve("gone.pdf");
        long cid = setupContent(nonexistent);

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        // item should be back as PENDING (retry) since attempts=1 < maxAttempts=3
        assertThat(workItemStatus(cid)).isIn("PENDING", "FAILED");
    }

    @Test
    void unsupportedTypeCompletesWithoutStoringText() throws Exception {
        Path docx = tmp.resolve("notes.docx");
        Files.writeString(docx, "some content");
        long cid = setupContent(docx);

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(workItemStatus(cid)).isEqualTo("DONE");
        // No text row stored
        int count = jdbc.sql("SELECT count(*) FROM content_text WHERE content_id = ?")
                .param(cid).query(Integer.class).single();
        assertThat(count).isZero();
    }

    @Test
    void blankPdfCompletesWithNoneSource() throws Exception {
        long cid = setupContent(blankPdf());

        org.junit.jupiter.api.Assumptions.assumeTrue(tesseractAvailable,
                "Skipping blank PDF test: Tesseract not available");

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(storedSource(cid)).isEqualTo("NONE");
        assertThat(workItemStatus(cid)).isEqualTo("DONE");
    }

    @Test
    void emptyQueueReturnsFalse() {
        assertThat(service.processNext()).isFalse();
    }

    @Test
    void imagePdfIsOcrd() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tesseractAvailable,
                "Skipping image OCR test: Tesseract not available");

        // Build an image-only PDF by embedding a raster image
        BufferedImage img = new BufferedImage(400, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.PLAIN, 30));
        g.drawString("HELLO OCR", 30, 90);
        g.dispose();

        Path file = tmp.resolve("image_only.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            var pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 0, 600, 400, 120);
            }
            doc.save(file.toFile());
        }
        long cid = setupContent(file);

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(storedSource(cid)).isEqualTo("OCR");
        assertThat(workItemStatus(cid)).isEqualTo("DONE");
    }

    @Test
    void mixedPdfStoresTextLayerAndOcrPages() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(tesseractAvailable,
                "Skipping mixed PDF test: Tesseract not available");

        BufferedImage img = new BufferedImage(400, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.PLAIN, 30));
        g.drawString("SCANNED PAGE", 30, 90);
        g.dispose();

        Path file = tmp.resolve("mixed.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage textPage = new PDPage(PDRectangle.A4);
            doc.addPage(textPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, textPage)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Embedded layer page");
                cs.endText();
            }
            PDPage imagePage = new PDPage(PDRectangle.A4);
            doc.addPage(imagePage);
            var pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, imagePage)) {
                cs.drawImage(pdImage, 0, 600, 400, 120);
            }
            doc.save(file.toFile());
        }
        long cid = setupContent(file);

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(storedSource(cid)).isEqualTo("OCR");
        String text = jdbc.sql("SELECT text FROM content_text WHERE content_id = ?")
                .param(cid).query(String.class).single();
        assertThat(text).contains("Embedded layer page");
        assertThat(text).containsIgnoringCase("scanned");
        assertThat(workItemStatus(cid)).isEqualTo("DONE");
    }

    @Test
    void fileChangedSinceScanFailsItemAndStoresNothing() throws Exception {
        Path file = textPdf("Original content");
        long cid = setupContent(file);
        Files.copy(textPdf("Overwritten content"), file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(workItemStatus(cid)).isIn("PENDING", "FAILED");
        int count = jdbc.sql("SELECT count(*) FROM content_text WHERE content_id = ?")
                .param(cid).query(Integer.class).single();
        assertThat(count).isZero();
    }

    @Test
    void secondLocationIsUsedWhenFirstChanged() throws Exception {
        Path changed = textPdf("Changed copy");
        Path intact = textPdf("Intact copy");
        long cid = insertContent(hasher.hash(intact).sha256(), Files.size(intact));
        addLocation(cid, changed);
        addLocation(cid, intact);
        workQueue.enqueue(QueueName.PROCESS, cid);

        boolean processed = service.processNext();

        assertThat(processed).isTrue();
        assertThat(workItemStatus(cid)).isEqualTo("DONE");
        String text = jdbc.sql("SELECT text FROM content_text WHERE content_id = ?")
                .param(cid).query(String.class).single();
        assertThat(text).contains("Intact copy");
    }
}
