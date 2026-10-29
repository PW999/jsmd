package com.pw999.jsmd.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdfExtractorTest {

    private PdfTextReader textReader;
    private OcrService ocrService;
    private PdfExtractor extractor;
    private final Path dummyPath = Path.of("dummy.pdf");

    @BeforeEach
    void setUp() {
        textReader = mock(PdfTextReader.class);
        ocrService = mock(OcrService.class);
        extractor = new PdfExtractor(textReader, ocrService);
    }

    @Test
    void textLayerUsedWhenEveryPageHasText() throws IOException {
        when(textReader.readPages(dummyPath)).thenReturn(List.of("Page one", "Page two"));

        ExtractionResult result = extractor.extract(dummyPath);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.TEXT_LAYER);
        assertThat(result.text()).isEqualTo("Page one\f\nPage two");
        verify(ocrService, never()).recognisePdfPages(any(), any());
        verify(ocrService, never()).recognisePdf(any());
    }

    @Test
    void mixedPdfOcrsOnlyBlankPagesAndKeepsPageOrder() throws IOException {
        when(textReader.readPages(dummyPath)).thenReturn(List.of("Layer one", " ", "", "Layer four"));
        when(ocrService.recognisePdfPages(dummyPath, List.of(1, 2))).thenReturn(List.of("Scan two", "Scan three"));

        ExtractionResult result = extractor.extract(dummyPath);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.OCR);
        assertThat(result.text()).isEqualTo("Layer one\f\nScan two\f\nScan three\f\nLayer four");
    }

    @Test
    void mixedPdfWhereOcrFindsNothingKeepsTextLayerSource() throws IOException {
        when(textReader.readPages(dummyPath)).thenReturn(List.of("Layer one", ""));
        when(ocrService.recognisePdfPages(dummyPath, List.of(1))).thenReturn(List.of("  "));

        ExtractionResult result = extractor.extract(dummyPath);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.TEXT_LAYER);
        assertThat(result.text()).contains("Layer one");
    }

    @Test
    void allBlankPdfUsesOcrForEveryPage() throws IOException {
        when(textReader.readPages(dummyPath)).thenReturn(List.of("", ""));
        when(ocrService.recognisePdfPages(dummyPath, List.of(0, 1))).thenReturn(List.of("Scan one", "Scan two"));

        ExtractionResult result = extractor.extract(dummyPath);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.OCR);
        assertThat(result.text()).isEqualTo("Scan one\f\nScan two");
    }

    @Test
    void noneWhenBothLayerAndOcrAreEmpty() throws IOException {
        when(textReader.readPages(dummyPath)).thenReturn(List.of("", ""));
        when(ocrService.recognisePdfPages(dummyPath, List.of(0, 1))).thenReturn(List.of("  ", ""));

        ExtractionResult result = extractor.extract(dummyPath);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.NONE);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void whitespaceOnlyLayerIsTreatedAsBlank() throws IOException {
        when(textReader.readPages(dummyPath)).thenReturn(List.of("\n\r\t  "));
        when(ocrService.recognisePdfPages(dummyPath, List.of(0))).thenReturn(List.of("Scanned text"));

        ExtractionResult result = extractor.extract(dummyPath);

        assertThat(result.source()).isEqualTo(ExtractionResult.Source.OCR);
        assertThat(result.text()).isEqualTo("Scanned text");
    }
}
