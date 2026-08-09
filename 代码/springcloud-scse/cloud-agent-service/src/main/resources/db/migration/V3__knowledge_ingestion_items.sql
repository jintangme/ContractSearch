-- Phase 2B resumable/idempotent ingestion ledger.

CREATE TABLE IF NOT EXISTS knowledge_source_alias (
    id BIGSERIAL PRIMARY KEY,
    corpus_version_id UUID NOT NULL REFERENCES knowledge_corpus_version(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
    existing_contract_id BIGINT NOT NULL,
    source_ref TEXT NOT NULL,
    raw_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_source_alias_contract UNIQUE(corpus_version_id, existing_contract_id)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_source_alias_source
    ON knowledge_source_alias(source_id);

CREATE TABLE IF NOT EXISTS knowledge_ingestion_item (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES knowledge_ingestion_job(id) ON DELETE CASCADE,
    existing_contract_id BIGINT NOT NULL,
    source_id UUID REFERENCES knowledge_source(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    document_count INT NOT NULL DEFAULT 0,
    chunk_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_ingestion_item_contract UNIQUE(job_id, existing_contract_id),
    CONSTRAINT chk_knowledge_ingestion_item_status
        CHECK (status IN ('SUCCEEDED','PARTIAL','FAILED','SKIPPED')),
    CONSTRAINT chk_knowledge_ingestion_item_counts
        CHECK (document_count >= 0 AND chunk_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_ingestion_item_job_status
    ON knowledge_ingestion_item(job_id, status);
