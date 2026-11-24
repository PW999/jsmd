# Spec Delta

## MODIFIED Requirements

### Requirement: Storing extracted text
The system SHALL store at most one extracted text per content, replacing it when the content is processed again, so that reprocessing is idempotent. Each stored text SHALL be accompanied by its embedding, so the vector is replaced together with the text on reprocessing.

#### Scenario: Reprocessed content
- **WHEN** an item for already-processed content is processed again
- **THEN** the stored text is replaced and no second row exists

#### Scenario: Embedding stored with text
- **WHEN** the system stores extracted text for a content item
- **THEN** the text is embedded and the vector is stored alongside it

#### Scenario: Re-extracted content
- **WHEN** the text of a content item is replaced
- **THEN** its vector is replaced to match the new text

## ADDED Requirements

### Requirement: Embedding generation
The system SHALL generate the embedding for stored text using the configured embedding model, defaulting to `nomic-embed-text:v1.5`, and SHALL store it as part of the same operation. The embedding model name and endpoint SHALL be configurable.

#### Scenario: Embedding generated
- **WHEN** extracted text is stored and the embedding service is reachable
- **THEN** the embedding is generated from that text and stored with it

#### Scenario: Configurable model
- **WHEN** the embedding model is configured to a name other than the default
- **THEN** embeddings are generated with the configured model

### Requirement: Embedding failure fails the item
The system SHALL treat an embedding failure as a processing failure: the queue item SHALL be marked failed and retried per the queue rules, and text SHALL NOT be stored without a vector.

#### Scenario: Embedding service unavailable
- **WHEN** the embedding service cannot be reached while processing an item
- **THEN** the item is marked failed with the error and retried per the queue rules

#### Scenario: Item succeeds after retry
- **WHEN** a failed item is retried once the embedding service is reachable
- **THEN** the text is embedded, stored, and the item is marked done