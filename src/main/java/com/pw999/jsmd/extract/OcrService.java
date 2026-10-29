package com.pw999.jsmd.extract;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Runs OCR on image-only PDFs and images using {@link TesseractOcrEngine}.
 *
 * <p>Each call to {@link #recognisePdf} or {@link #recogniseImage} creates one engine session,
 * processes all pages, and always closes the session.
 *
 * <p>Pages are rendered and recognised one at a time to bound memory usage.
 */
@Service
@EnableConfigurationProperties(ExtractProperties.class)
public class OcrService {

    /**
     * Mapping from ISO 639-1 two-letter codes to Tesseract three-letter codes.
     * Three-letter codes are passed through unchanged.
     */
    private static final Map<String, String> ISO_TO_TESS = Map.ofEntries(
            Map.entry("af", "afr"),
            Map.entry("sq", "sqi"),
            Map.entry("ar", "ara"),
            Map.entry("az", "aze"),
            Map.entry("eu", "eus"),
            Map.entry("be", "bel"),
            Map.entry("bn", "ben"),
            Map.entry("bs", "bos"),
            Map.entry("bg", "bul"),
            Map.entry("ca", "cat"),
            Map.entry("zh", "chi_sim"),
            Map.entry("hr", "hrv"),
            Map.entry("cs", "ces"),
            Map.entry("da", "dan"),
            Map.entry("nl", "nld"),
            Map.entry("en", "eng"),
            Map.entry("et", "est"),
            Map.entry("fi", "fin"),
            Map.entry("fr", "fra"),
            Map.entry("gl", "glg"),
            Map.entry("de", "deu"),
            Map.entry("el", "ell"),
            Map.entry("he", "heb"),
            Map.entry("hi", "hin"),
            Map.entry("hu", "hun"),
            Map.entry("id", "ind"),
            Map.entry("ga", "gle"),
            Map.entry("it", "ita"),
            Map.entry("ja", "jpn"),
            Map.entry("ko", "kor"),
            Map.entry("lv", "lav"),
            Map.entry("lt", "lit"),
            Map.entry("mk", "mkd"),
            Map.entry("ms", "msa"),
            Map.entry("mt", "mlt"),
            Map.entry("no", "nor"),
            Map.entry("fa", "fas"),
            Map.entry("pl", "pol"),
            Map.entry("pt", "por"),
            Map.entry("ro", "ron"),
            Map.entry("ru", "rus"),
            Map.entry("sr", "srp"),
            Map.entry("sk", "slk"),
            Map.entry("sl", "slv"),
            Map.entry("es", "spa"),
            Map.entry("sw", "swa"),
            Map.entry("sv", "swe"),
            Map.entry("tl", "tgl"),
            Map.entry("ta", "tam"),
            Map.entry("te", "tel"),
            Map.entry("th", "tha"),
            Map.entry("tr", "tur"),
            Map.entry("uk", "ukr"),
            Map.entry("ur", "urd"),
            Map.entry("vi", "vie"),
            Map.entry("cy", "cym"),
            Map.entry("yi", "yid")
    );

    private final ExtractProperties properties;

    public OcrService(ExtractProperties properties) {
        this.properties = properties;
    }

    /**
     * Renders each page of the PDF at the configured DPI and runs OCR on it.
     * Pages are joined with a form-feed + newline separator.
     *
     * @param path path to a PDF file
     * @return combined recognised text across all pages
     * @throws IOException  if the PDF cannot be read
     * @throws OcrException if OCR fails
     */
    public String recognisePdf(Path path) throws IOException {
        return String.join("\f\n", recognisePages(path, null));
    }

    /**
     * Renders only the given pages of the PDF at the configured DPI and runs OCR on them,
     * using a single engine session.
     *
     * @param path    path to a PDF file
     * @param indexes zero-based page indexes to recognise
     * @return recognised text per requested page, in the order of {@code indexes}
     * @throws IOException  if the PDF cannot be read
     * @throws OcrException if OCR fails
     */
    public List<String> recognisePdfPages(Path path, List<Integer> indexes) throws IOException {
        return recognisePages(path, indexes);
    }

    /** Recognises the given pages, or all pages when {@code indexes} is null. */
    private List<String> recognisePages(Path path, List<Integer> indexes) throws IOException {
        String joinedLangs = buildJoinedLanguages();
        checkTrainedDataFiles(joinedLangs);
        try (PDDocument doc = Loader.loadPDF(path.toFile());
             OcrEngine engine = createEngine(joinedLangs)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            List<Integer> pages = indexes != null
                    ? indexes
                    : IntStream.range(0, doc.getNumberOfPages()).boxed().toList();
            List<String> pageTexts = new ArrayList<>(pages.size());
            for (int i : pages) {
                BufferedImage image = renderer.renderImageWithDPI(i, properties.renderDpi());
                pageTexts.add(engine.recognise(image, properties.renderDpi()));
            }
            return pageTexts;
        }
    }

    /**
     * Runs OCR on a single image (already decoded as a {@link BufferedImage}).
     *
     * @param image the image to recognise
     * @return the recognised text
     * @throws OcrException if OCR fails
     */
    public String recogniseImage(BufferedImage image) {
        String joinedLangs = buildJoinedLanguages();
        checkTrainedDataFiles(joinedLangs);
        try (OcrEngine engine = createEngine(joinedLangs)) {
            return engine.recognise(image);
        }
    }

    /**
     * Runs OCR on several images (e.g. the pages of a TIFF) using a single engine session.
     *
     * @param count number of images
     * @param image supplies the image at a given index; read lazily to bound memory usage
     * @return recognised text per image
     * @throws OcrException if OCR fails
     */
    public List<String> recogniseImages(int count, IntFunction<BufferedImage> image) {
        String joinedLangs = buildJoinedLanguages();
        checkTrainedDataFiles(joinedLangs);
        try (OcrEngine engine = createEngine(joinedLangs)) {
            List<String> texts = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                texts.add(engine.recognise(image.apply(i)));
            }
            return texts;
        }
    }

    // --- package-private for testing ---

    /** Converts configured language list to a {@code +}-joined Tesseract string (e.g. {@code eng+nld}). */
    String buildJoinedLanguages() {
        return properties.ocrLanguages().stream()
                .map(OcrService::toTesseractCode)
                .collect(Collectors.joining("+"));
    }

    /** Maps a two-letter ISO 639-1 code to the Tesseract three-letter code; three-letter codes pass through. */
    static String toTesseractCode(String lang) {
        if (lang.length() == 2) {
            return ISO_TO_TESS.getOrDefault(lang, lang);
        }
        return lang;
    }

    /** Creates one engine session; overridable in tests. */
    OcrEngine createEngine(String joinedLangs) {
        return new TesseractOcrEngine(
                properties.tesseractLibrary(),
                properties.tessdataPath(),
                joinedLangs);
    }

    /**
     * When {@code tessdata-path} is configured, verifies that each required {@code .traineddata} file exists.
     * A missing file would cause Tesseract to return no text, which the spec treats as a failure rather than
     * empty output, so we raise an error early.
     */
    private void checkTrainedDataFiles(String joinedLangs) {
        Path tessdata = properties.tessdataPath();
        if (tessdata == null) {
            return;
        }
        for (String tessCode : joinedLangs.split("\\+")) {
            Path traineddata = tessdata.resolve(tessCode + ".traineddata");
            if (!Files.isRegularFile(traineddata)) {
                throw new OcrException(
                        "Tesseract language data not found: " + traineddata
                        + " (configured tessdata-path=" + tessdata + ", language=" + tessCode + ")");
            }
        }
    }
}
