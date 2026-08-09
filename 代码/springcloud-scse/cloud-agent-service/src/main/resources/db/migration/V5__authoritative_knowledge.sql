-- Delivery Stage 2.5: allow the shared Knowledge search channel to index
-- authoritative references that are not tied to a catalog contract.

ALTER TABLE knowledge_chunk_search
    ALTER COLUMN existing_contract_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_source_corpus_kind_trust
    ON knowledge_source(corpus_version_id, source_kind, trust_level);

CREATE INDEX IF NOT EXISTS idx_knowledge_source_reference
    ON knowledge_source(corpus_version_id, source_ref)
    WHERE existing_contract_id IS NULL;
