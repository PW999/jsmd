package com.pw999.jsmd.extract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Extracts text from a PDF page by page: pages with an embedded text layer use it, pages without one are
 * OCR'd from the rendered page, and the page texts are combined in page order. A PDF that yields no text
 * at all is not an error — it completes with {@link ExtractionResult.Source#NONE} and empty text.
 */
@Component
public class PdfExtractor {

    /** Separator between pages, the same one {@link OcrService#recognisePdf} uses. */
    private static final String PAGE_SEPARATOR = "\f\n";

    private final PdfTextReader textReader;
    private final OcrService ocrService;

    public PdfExtractor(PdfTextReader textReader, OcrService ocrService) {
        this.textReader = textReader;
        this.ocrService = ocrService;
    }

    /**
     * Extracts text from the given PDF path.
     *
     * @param path path to a {@code .pdf} file
     * @return the extraction result with text and source indicator
     * @throws IOException if the PDF cannot be read
     */
    public ExtractionResult extract(Path path) throws IOException {
        List<String> pages = new ArrayList<>(textReader.readPages(path));
        boolean anyLayerText = false;
        List<Integer> blankPages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            if (hasText(pages.get(i))) {
                anyLayerText = true;
            } else {
                blankPages.add(i);
            }
        }

        boolean ocrAddedText = false;
        if (!blankPages.isEmpty()) {
            List<String> ocrTexts = ocrService.recognisePdfPages(path, blankPages);
            for (int i = 0; i < blankPages.size(); i++) {
                String ocrText = ocrTexts.get(i);
                if (hasText(ocrText)) {
                    pages.set(blankPages.get(i), ocrText);
                    ocrAddedText = true;
                }
            }
        }

        if (ocrAddedText) {
            return new ExtractionResult(String.join(PAGE_SEPARATOR, pages), ExtractionResult.Source.OCR);
        }
        if (anyLayerText) {
            return new ExtractionResult(String.join(PAGE_SEPARATOR, pages), ExtractionResult.Source.TEXT_LAYER);
        }
        return new ExtractionResult("", ExtractionResult.Source.NONE);
    }

    /** Returns true when the text contains at least one non-whitespace character. */
    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
