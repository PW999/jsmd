package com.pw999.jsmd.web;

import com.pw999.jsmd.document.DocumentDetail;
import com.pw999.jsmd.document.DocumentDownload;
import com.pw999.jsmd.document.DocumentDownloadService;
import com.pw999.jsmd.document.DocumentNotFoundException;
import com.pw999.jsmd.document.DocumentRepository;
import com.pw999.jsmd.document.SearchResult;
import com.pw999.jsmd.embed.EmbeddingService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Lists indexed files, shows a file's extracted text and downloads the original file.
 * Documents are addressed by id only; paths always come from the database.
 */
@Controller
class DocumentsController {

    static final int PAGE_SIZE = 50;
    static final int MAX_QUERY_LENGTH = 200;
    static final String MODE_FULL_TEXT = "fts";
    static final String MODE_VECTOR = "vector";

    private static final Logger log = LoggerFactory.getLogger(DocumentsController.class);

    private final DocumentRepository documents;
    private final DocumentDownloadService downloads;
    private final EmbeddingService embeddings;

    DocumentsController(DocumentRepository documents, DocumentDownloadService downloads, EmbeddingService embeddings) {
        this.documents = documents;
        this.downloads = downloads;
        this.embeddings = embeddings;
    }

    @GetMapping("/documents")
    String list(@RequestParam(defaultValue = "0") int page,
                @RequestParam(name = "q", required = false) String q,
                @RequestParam(name = "mode", required = false) String mode,
                Model model) {
        String query = normalise(q);
        boolean searching = !query.isEmpty();
        boolean vector = searching && MODE_VECTOR.equalsIgnoreCase(mode);
        float[] queryVector = vector ? embedOrNull(query) : null;
        boolean vectorUnavailable = vector && queryVector == null;

        long total;
        if (vector && !vectorUnavailable) {
            total = documents.countVectorMatches();
        } else if (searching) {
            total = documents.countMatches(query);
        } else {
            total = documents.count();
        }
        int lastPage = (int) Math.max(0, (total - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 0), lastPage);

        model.addAttribute("active", "documents");
        model.addAttribute("searching", searching);
        model.addAttribute("query", query);
        model.addAttribute("mode", vector ? MODE_VECTOR : MODE_FULL_TEXT);
        if (vector && vectorUnavailable) {
            model.addAttribute("results", List.of());
        } else if (vector) {
            model.addAttribute("results", documents.searchVector(queryVector, current, PAGE_SIZE));
        } else if (searching) {
            model.addAttribute("results", documents.search(query, current, PAGE_SIZE));
        } else {
            model.addAttribute("documents", documents.list(current, PAGE_SIZE));
        }
        model.addAttribute("total", total);
        model.addAttribute("page", current);
        model.addAttribute("hasPrevious", current > 0);
        model.addAttribute("hasNext", current < lastPage);
        return "documents";
    }

    /**
     * Embeds a query for vector search, or returns null when the embedding service is unavailable or disabled;
     * the caller then shows no results instead of failing.
     */
    private float[] embedOrNull(String query) {
        try {
            return embeddings.embed(query);
        } catch (RuntimeException e) {
            log.warn("Vector search unavailable: {}", e.getMessage());
            return null;
        }
    }

    private static String normalise(String q) {
        if (q == null) {
            return "";
        }
        String trimmed = q.strip();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH).stripTrailing() : trimmed;
    }

    @GetMapping("/documents/{id}")
    String detail(@PathVariable long id, Model model) {
        DocumentDetail document = documents.find(id).orElseThrow(() -> new DocumentNotFoundException(id));
        model.addAttribute("active", "documents");
        model.addAttribute("document", document);
        return "document";
    }

    @GetMapping("/documents/{id}/download")
    ResponseEntity<Resource> download(@PathVariable long id) {
        DocumentDownload file = downloads.prepare(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(file.contentType())
                .body(file.resource());
    }
}
