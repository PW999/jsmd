package com.pw999.jsmd.embed;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama-backed embedding model used for vector search and text embedding.
 */
@Configuration
@EnableConfigurationProperties(EmbedProperties.class)
public class EmbedConfig {

    @Bean
    @ConditionalOnProperty(prefix = "jsmd.embed", name = "enabled", havingValue = "true", matchIfMissing = true)
    OllamaEmbeddingModel embeddingModel(EmbedProperties properties) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(properties.baseUri())
                .modelName(properties.model())
                .dimensions(properties.dimension())
                .timeout(properties.timeout())
                .build();
    }
}
