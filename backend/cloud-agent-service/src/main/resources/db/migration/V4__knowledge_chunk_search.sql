-- Delivery Stage 2: additive chunk-level text retrieval index.
-- This table remains a shadow retrieval channel; existing Search tables/indexes are unchanged.

CREATE TABLE IF NOT EXISTS knowledge_chunk_search (
    chunk_id UUID PRIMARY KEY REFERENCES knowledge_chunk(id) ON DELETE CASCADE,
    corpus_version_id UUID NOT NULL REFERENCES knowledge_corpus_version(id) ON DELETE CASCADE,
    existing_contract_id BIGINT NOT NULL,
    search_text TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS
        (to_tsvector('simple', search_text)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_search_vector
    ON knowledge_chunk_search USING GIN(search_vector);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_search_corpus_contract
    ON knowledge_chunk_search(corpus_version_id, existing_contract_id);
