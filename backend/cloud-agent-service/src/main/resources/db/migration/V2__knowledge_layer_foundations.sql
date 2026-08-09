-- Phase 2A additive Knowledge Layer foundations.
-- This migration deliberately does not create or switch an active retrieval index.
-- Existing contract/search tables remain the production source of truth.

CREATE TABLE IF NOT EXISTS knowledge_corpus_version (
    id UUID PRIMARY KEY,
    corpus_name VARCHAR(128) NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'BUILDING',
    manifest_hash CHAR(64),
    embedding_model VARCHAR(128),
    embedding_dimension INT,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_knowledge_corpus_name_version UNIQUE(corpus_name, version),
    CONSTRAINT chk_knowledge_corpus_status
        CHECK (status IN ('BUILDING','READY','ACTIVE','RETIRED','FAILED')),
    CONSTRAINT chk_knowledge_embedding_dimension
        CHECK (embedding_dimension IS NULL OR embedding_dimension > 0)
);

CREATE TABLE IF NOT EXISTS knowledge_source (
    id UUID PRIMARY KEY,
    corpus_version_id UUID NOT NULL REFERENCES knowledge_corpus_version(id) ON DELETE CASCADE,
    source_kind VARCHAR(64) NOT NULL,
    source_ref TEXT NOT NULL,
    existing_contract_id BIGINT,
    trust_level VARCHAR(32) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    owner_user_id BIGINT,
    raw_hash CHAR(64) NOT NULL,
    normalized_hash CHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_source_identity
        UNIQUE(corpus_version_id, source_ref, raw_hash),
    CONSTRAINT chk_knowledge_source_trust
        CHECK (trust_level IN ('VERIFIED','AUTHORITATIVE','CURATED','COMMUNITY','USER_PROVIDED','TOOL_GENERATED','UNTRUSTED')),
    CONSTRAINT chk_knowledge_source_visibility
        CHECK (visibility IN ('PUBLIC','PRIVATE','WORKSPACE')),
    CONSTRAINT chk_knowledge_source_owner
        CHECK (visibility = 'PUBLIC' OR owner_user_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_source_corpus_contract
    ON knowledge_source(corpus_version_id, existing_contract_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_source_normalized_hash
    ON knowledge_source(corpus_version_id, normalized_hash);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
    document_kind VARCHAR(64) NOT NULL,
    language VARCHAR(32) NOT NULL,
    source_format VARCHAR(64) NOT NULL,
    parser_status VARCHAR(32) NOT NULL,
    file_path TEXT NOT NULL DEFAULT '',
    raw_content TEXT NOT NULL,
    normalized_content TEXT NOT NULL,
    raw_hash CHAR(64) NOT NULL,
    normalized_hash CHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_document_file UNIQUE(source_id, file_path),
    CONSTRAINT chk_knowledge_document_parser_status
        CHECK (parser_status IN ('PARSED','PARTIAL','INVALID'))
);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_normalized_hash
    ON knowledge_document(normalized_hash);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    ordinal INT NOT NULL,
    chunk_kind VARCHAR(32) NOT NULL,
    symbol_name TEXT,
    signature TEXT,
    file_path TEXT NOT NULL DEFAULT '',
    line_start INT NOT NULL,
    line_end INT NOT NULL,
    char_start INT NOT NULL,
    char_end INT NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_chunk_ordinal UNIQUE(document_id, ordinal),
    CONSTRAINT chk_knowledge_chunk_line_range CHECK (line_start > 0 AND line_end >= line_start),
    CONSTRAINT chk_knowledge_chunk_char_range CHECK (char_start >= 0 AND char_end > char_start)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_document_kind
    ON knowledge_chunk(document_id, chunk_kind);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_content_hash
    ON knowledge_chunk(content_hash);

CREATE TABLE IF NOT EXISTS knowledge_ingestion_job (
    id UUID PRIMARY KEY,
    corpus_version_id UUID NOT NULL REFERENCES knowledge_corpus_version(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    mode VARCHAR(32) NOT NULL DEFAULT 'DRY_RUN',
    checkpoint TEXT,
    source_count INT NOT NULL DEFAULT 0,
    document_count INT NOT NULL DEFAULT 0,
    chunk_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_knowledge_ingestion_status
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','PARTIAL','FAILED','CANCELLED')),
    CONSTRAINT chk_knowledge_ingestion_mode
        CHECK (mode IN ('DRY_RUN','INCREMENTAL','FULL_REBUILD')),
    CONSTRAINT chk_knowledge_ingestion_counts
        CHECK (source_count >= 0 AND document_count >= 0 AND chunk_count >= 0 AND error_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_ingestion_job_corpus_created
    ON knowledge_ingestion_job(corpus_version_id, created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_ingestion_error (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES knowledge_ingestion_job(id) ON DELETE CASCADE,
    source_ref TEXT,
    stage VARCHAR(64) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    sanitized_message TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_ingestion_error_job_stage
    ON knowledge_ingestion_error(job_id, stage);
