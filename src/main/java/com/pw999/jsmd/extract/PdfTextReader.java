package com.pw999.jsmd.extract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads the embedded text layer of a PDF using Apache PDFBox.
 * Returns an empty string when the document has no usable text layer.
 */
@Component
public class PdfTextReader {

    /**
     * Extracts the text layer from the given PDF file.
     *
     * @param path path to a PDF file
     * @return the embedded text, or an empty string if none is present
     * @throws IOException if the file cannot be read or is not a valid PDF
     */
    public String read(Path path) throws IOException {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /**
     * Extracts the text layer of each page separately.
     *
     * @param path path to a PDF file
     * @return one string per page, in page order; a page without a text layer yields a blank string
     * @throws IOException if the file cannot be read or is not a valid PDF
     */
    public List<String> readPages(Path path) throws IOException {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = doc.getNumberOfPages();
            List<String> pages = new ArrayList<>(pageCount);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(doc));
            }
            return pages;
        }
    }
}
