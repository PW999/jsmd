package com.pw999.jsmd.embed;

import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Embeds text into vectors through the configured Ollama embedding model.
 *
 * <p>Embedding is disabled by configuration: when disabled no vector is produced and text is stored without one.
 */
@Service
public class EmbeddingService {

    private final EmbedProperties properties;
    private final ObjectProvider<EmbeddingModel> embeddingModel;

    public EmbeddingService(EmbedProperties properties, ObjectProvider<EmbeddingModel> embeddingModel) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
    }

    /** Returns whether the application embeds extracted text. */
    public boolean enabled() {
        return properties.enabled();
    }

    /** Returns the configured embedding vector dimension. */
    public int dimension() {
        return properties.dimension();
    }

    /**
     * Embeds the text and returns the vector, or {@code null} when embedding is disabled.
     *
     * @param text the text to embed
     * @return the embedding vector, or null when disabled
     * @throws RuntimeException when the embedding model cannot be reached
     */
    public float[] embed(String text) {
        if (!properties.enabled()) {
            return null;
        }
        return embeddingModel.getObject().embed(text).content().vector();
    }
}
