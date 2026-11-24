package com.pw999.jsmd.embed;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for text embedding through the Ollama embedding model.
 *
 * @param enabled    master switch; when false no vectors are generated or stored and vector search returns no results
 * @param baseUri    Ollama server base URI
 * @param model      name of the Ollama embedding model
 * @param dimension  embedding vector dimension; must match the {@code vector(768)} column
 * @param timeout    per-request timeout for the embedding calls
 */
@Validated
@ConfigurationProperties("jsmd.embed")
public record EmbedProperties(
        boolean enabled,
        @NotBlank String baseUri,
        @NotBlank String model,
        @Min(1) int dimension,
        @NotNull Duration timeout) {
}
