package com.pw999.jsmd.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.pw999.jsmd.document.DocumentDetail;
import com.pw999.jsmd.document.DocumentDownload;
import com.pw999.jsmd.document.DocumentDownloadService;
import com.pw999.jsmd.document.DocumentNotFoundException;
import com.pw999.jsmd.document.DocumentRepository;
import com.pw999.jsmd.queue.QueueMonitor;
import com.pw999.jsmd.document.DocumentRow;
import com.pw999.jsmd.document.ExtractionState;
import com.pw999.jsmd.document.FileChangedException;
import com.pw999.jsmd.document.FileUnavailableException;
import com.pw999.jsmd.document.SearchResult;
import com.pw999.jsmd.document.Snippet;
import com.pw999.jsmd.security.SecurityConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = {"jsmd.admin.username=admin", "jsmd.admin.password=s3cret-pw"})
@Import(SecurityConfig.class)
@WithMockUser(username = "admin")
class DocumentsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DocumentRepository documents;

    @MockitoBean
    DocumentDownloadService downloads;

    @MockitoBean
    QueueMonitor queueMonitor;

    private static DocumentDetail detail(long id, String path, ExtractionState state, String text, Integer pages) {
        return new DocumentDetail(id, path, 2048, state, text, pages, new byte[]{1});
    }

    @Test
    void listShowsRowsWithLinksIconsAndState() throws Exception {
        given(documents.count()).willReturn(2L);
        given(documents.list(0, 50)).willReturn(List.of(
                new DocumentRow(5, "/data/a.pdf", 1234, ExtractionState.OCR),
                new DocumentRow(6, "/data/b.pdf", 10, ExtractionState.PENDING)));

        mvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<header class=\"banner\">")))
                .andExpect(content().string(containsString("href=\"/documents/5\"")))
                .andExpect(content().string(containsString("href=\"/documents/5/download\"")))
                .andExpect(content().string(containsString("aria-label=\"Download a.pdf\"")))
                .andExpect(content().string(containsString("/data/b.pdf")))
                .andExpect(content().string(containsString("1,234 B")))
                .andExpect(content().string(containsString(">OCR<")))
                .andExpect(content().string(containsString(">Pending<")))
                .andExpect(content().string(containsString("aria-current=\"page\" class=\"active\">Documents</a>")))
                .andExpect(content().string(not(containsString("Previous"))))
                .andExpect(content().string(not(containsString("Next"))));
    }

    @Test
    void emptyListStatesNothingIsIndexed() throws Exception {
        given(documents.count()).willReturn(0L);
        given(documents.list(0, 50)).willReturn(List.of());

        mvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No documents have been indexed yet.")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void middlePageOffersPreviousAndNext() throws Exception {
        given(documents.count()).willReturn(120L);
        given(documents.list(1, 50)).willReturn(List.of(new DocumentRow(1, "/a.pdf", 1, ExtractionState.NONE)));

        mvc.perform(get("/documents").param("page", "1"))
                .andExpect(content().string(containsString("href=\"/documents?page=0\"")))
                .andExpect(content().string(containsString("href=\"/documents?page=2\"")))
                .andExpect(content().string(containsString("Page 2")));
    }

    @Test
    void pageParameterIsClampedToValidRange() throws Exception {
        given(documents.count()).willReturn(120L);
        given(documents.list(anyInt(), anyInt()))
                .willReturn(List.of(new DocumentRow(1, "/a.pdf", 1, ExtractionState.NONE)));

        mvc.perform(get("/documents").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/documents?page=1\"")))
                .andExpect(content().string(not(containsString("Next"))));
        mvc.perform(get("/documents").param("page", "-4"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Previous"))));
    }

    private static SearchResult result(long id, String path, String plain, String match) {
        return new SearchResult(id, path, 2048, new Snippet(List.of(
                new Snippet.Segment(plain, false), new Snippet.Segment(match, true))));
    }

    @Test
    void listShowsSearchForm() throws Exception {
        given(documents.count()).willReturn(0L);
        given(documents.list(0, 50)).willReturn(List.of());

        mvc.perform(get("/documents"))
                .andExpect(content().string(containsString("<form class=\"search\"")))
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString(">Search</button>")));
    }

    @Test
    void blankQueryShowsNormalList() throws Exception {
        given(documents.count()).willReturn(1L);
        given(documents.list(0, 50)).willReturn(List.of(new DocumentRow(5, "/data/a.pdf", 1, ExtractionState.OCR)));

        mvc.perform(get("/documents").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">OCR<")));

        verify(documents, never()).search(anyString(), anyInt(), anyInt());
        verify(documents, never()).countMatches(anyString());
    }

    @Test
    void matchingQueryShowsResultsWithSnippetCountAndPrefilledField() throws Exception {
        given(documents.countMatches("invoice")).willReturn(3L);
        given(documents.search("invoice", 0, 50)).willReturn(List.of(
                result(5, "/data/a.pdf", "pay this ", "invoice")));

        mvc.perform(get("/documents").param("q", "  invoice "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"invoice\"")))
                .andExpect(content().string(containsString("3 files match")))
                .andExpect(content().string(containsString("href=\"/documents/5\"")))
                .andExpect(content().string(containsString("href=\"/documents/5/download\"")))
                .andExpect(content().string(containsString("/data/a.pdf")))
                .andExpect(content().string(containsString("pay this <mark>invoice</mark>")))
                .andExpect(content().string(not(containsString(">OCR<"))));
        verify(documents, never()).list(anyInt(), anyInt());
    }

    @Test
    void noMatchSaysSoAndLinksBackToList() throws Exception {
        given(documents.countMatches("zzz")).willReturn(0L);
        given(documents.search("zzz", 0, 50)).willReturn(List.of());

        mvc.perform(get("/documents").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No documents match")))
                .andExpect(content().string(containsString("href=\"/documents\"")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void searchPagingLinksKeepTheQuery() throws Exception {
        given(documents.countMatches("red bike")).willReturn(120L);
        given(documents.search("red bike", 1, 50)).willReturn(List.of(result(1, "/a.pdf", "a ", "red bike")));

        mvc.perform(get("/documents").param("q", "red bike").param("page", "1"))
                .andExpect(content().string(containsString("href=\"/documents?page=0&amp;q=red%20bike\"")))
                .andExpect(content().string(containsString("href=\"/documents?page=2&amp;q=red%20bike\"")))
                .andExpect(content().string(containsString("Page 2")));
    }

    @Test
    void searchPageIsClampedToValidRange() throws Exception {
        given(documents.countMatches("x")).willReturn(120L);
        given(documents.search(anyString(), anyInt(), anyInt())).willReturn(List.of(result(1, "/a.pdf", "", "x")));

        mvc.perform(get("/documents").param("q", "x").param("page", "99"))
                .andExpect(content().string(containsString("href=\"/documents?page=1&amp;q=x\"")))
                .andExpect(content().string(not(containsString("Next"))));
        verify(documents).search("x", 2, 50);
        mvc.perform(get("/documents").param("q", "x").param("page", "-4"))
                .andExpect(content().string(not(containsString("Previous"))));
        verify(documents).search("x", 0, 50);
    }

    @Test
    void overlongQueryIsTruncatedToTwoHundredCharacters() throws Exception {
        String longQuery = "a".repeat(250);
        given(documents.countMatches(anyString())).willReturn(0L);

        mvc.perform(get("/documents").param("q", longQuery)).andExpect(status().isOk());

        verify(documents).countMatches("a".repeat(200));
    }

    @Test
    void snippetMarkupIsEscapedAndOnlyMatchesAreMarked() throws Exception {
        given(documents.countMatches("script")).willReturn(1L);
        given(documents.search("script", 0, 50)).willReturn(List.of(result(5, "/a.pdf", "<script>alert(1)</script> & ", "script")));

        mvc.perform(get("/documents").param("q", "script"))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt; &amp; <mark>script</mark>")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void queryIsEscapedInTheForm() throws Exception {
        given(documents.countMatches("\"><script>")).willReturn(0L);

        mvc.perform(get("/documents").param("q", "\"><script>"))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(containsString("&quot;&gt;&lt;script&gt;")));
    }

    @Test
    void detailShowsMetadataTextAndDownloadIcon() throws Exception {
        given(documents.find(5)).willReturn(Optional.of(
                detail(5, "/data/a.pdf", ExtractionState.TEXT_LAYER, "line one\nline two", 4)));

        mvc.perform(get("/documents/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("document"))
                .andExpect(content().string(containsString("<header class=\"banner\">")))
                .andExpect(content().string(containsString("/data/a.pdf")))
                .andExpect(content().string(containsString("Text layer")))
                .andExpect(content().string(containsString("line one\nline two")))
                .andExpect(content().string(containsString("href=\"/documents/5/download\"")))
                .andExpect(content().string(containsString("href=\"/documents\"")))
                .andExpect(content().string(containsString("aria-current=\"page\" class=\"active\">Documents</a>")));
    }

    @Test
    void detailEscapesMarkupInText() throws Exception {
        given(documents.find(5)).willReturn(Optional.of(
                detail(5, "/a.pdf", ExtractionState.OCR, "<script>alert(1)</script> & more", 1)));

        mvc.perform(get("/documents/5"))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt; &amp; more")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void detailWithoutTextStatesNoTextAvailable() throws Exception {
        given(documents.find(5)).willReturn(Optional.of(detail(5, "/a.pdf", ExtractionState.PENDING, null, null)));
        given(documents.find(6)).willReturn(Optional.of(detail(6, "/b.pdf", ExtractionState.NONE, "", 1)));

        mvc.perform(get("/documents/5"))
                .andExpect(content().string(containsString("No text available.")))
                .andExpect(content().string(not(containsString("<pre"))));
        mvc.perform(get("/documents/6"))
                .andExpect(content().string(containsString("No text available.")));
    }

    @Test
    void unknownDocumentIsNotFoundInLayout() throws Exception {
        given(documents.find(99)).willReturn(Optional.empty());

        mvc.perform(get("/documents/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownDocumentDownloadRendersNotFoundPage() throws Exception {
        given(downloads.prepare(99)).willThrow(new DocumentNotFoundException(99));

        mvc.perform(get("/documents/99/download"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(containsString("<span class=\"username\">admin</span>")));
    }

    @Test
    void downloadSendsAttachmentWithOriginalNameAndType() throws Exception {
        given(downloads.prepare(5)).willReturn(new DocumentDownload(
                new ByteArrayResource("PDFDATA".getBytes()), "report.pdf", MediaType.APPLICATION_PDF));

        mvc.perform(get("/documents/5/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("report.pdf")))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("PDFDATA"));
    }

    @Test
    void downloadEncodesNonAsciiFileName() throws Exception {
        given(downloads.prepare(5)).willReturn(new DocumentDownload(
                new ByteArrayResource(new byte[0]), "façade ü.png", MediaType.IMAGE_PNG));

        mvc.perform(get("/documents/5/download"))
                .andExpect(header().string("Content-Disposition", containsString("UTF-8''fa%C3%A7ade%20%C3%BC.png")));
    }

    @Test
    void removedFileShowsNoLongerAvailable() throws Exception {
        given(downloads.prepare(5)).willThrow(new FileUnavailableException("The file is no longer available on disk."));

        mvc.perform(get("/documents/5/download"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("File not available")))
                .andExpect(content().string(containsString("The file is no longer available on disk.")))
                .andExpect(content().string(containsString("<header class=\"banner\">")));
    }

    @Test
    void changedFileShowsConflict() throws Exception {
        given(downloads.prepare(5)).willThrow(new FileChangedException("The file has changed since it was indexed."));

        mvc.perform(get("/documents/5/download"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("The file has changed since it was indexed.")));
    }

    @Test
    void nonNumericIdIsRejected() throws Exception {
        mvc.perform(get("/documents/abc")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void anonymousUsersAreRedirectedToLogin() throws Exception {
        for (String url : List.of("/documents", "/documents?q=x", "/documents/5", "/documents/5/download")) {
            mvc.perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }
}
