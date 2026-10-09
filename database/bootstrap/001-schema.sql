-- Clean installation schema for ContractSearch 2.0.
-- Contains structure only. No production dump, accounts or credentials.
-- Requires ParadeDB pg_search (pdb tokenizer types) and pgvector.
-- Verified with PostgreSQL 18.3, pg_search 0.23.0 and pgvector 0.8.1.
-- Apply to an empty demo database, then 002-demo.sql; Agent Flyway V1-V6 is additive.
\set ON_ERROR_STOP on
BEGIN;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_search;

CREATE TABLE IF NOT EXISTS user_table (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(60) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    email VARCHAR(64) NOT NULL,
    avatar_type VARCHAR(32),
    avatar_url TEXT,
    avatar_preset_key VARCHAR(64),
    avatar_history TEXT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS smart_contract (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(255) NOT NULL UNIQUE,
    contract_name VARCHAR(255) NOT NULL,
    source_code TEXT NOT NULL,
    abi TEXT,
    abi_status VARCHAR(32),
    abi_error_message TEXT,
    compiler_version VARCHAR(64),
    bytecode TEXT,
    category VARCHAR(255) NOT NULL,
    description TEXT,
    code_embedding vector(768),
    description_embedding vector(768),
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    is_effective BOOLEAN NOT NULL DEFAULT false,
    uploader_id BIGINT
);

CREATE TABLE IF NOT EXISTS group_table (
    id BIGSERIAL PRIMARY KEY,
    group_title VARCHAR(255) NOT NULL,
    group_description VARCHAR(16383) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS group_contract (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES group_table(id) ON DELETE CASCADE,
    contract_id BIGINT NOT NULL REFERENCES smart_contract(id) ON DELETE CASCADE,
    order_num INTEGER NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, contract_id),
    UNIQUE (group_id, order_num)
);

CREATE TABLE IF NOT EXISTS favorite_folder (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_folder_user_name ON favorite_folder(user_id, name);

CREATE TABLE IF NOT EXISTS user_favorite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_table(id) ON DELETE CASCADE,
    contract_id BIGINT NOT NULL REFERENCES smart_contract(id) ON DELETE CASCADE,
    folder_id BIGINT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, contract_id)
);
CREATE INDEX IF NOT EXISTS idx_user_favorite_user_id ON user_favorite(user_id);
CREATE INDEX IF NOT EXISTS idx_user_favorite_contract_id ON user_favorite(contract_id);

CREATE TABLE IF NOT EXISTS contract_check_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    input_mode VARCHAR(24),
    file_name VARCHAR(255),
    contract_name VARCHAR(255),
    source_code TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'success',
    summary TEXT,
    conclusion TEXT,
    risk_level VARCHAR(24),
    key_issues TEXT,
    suggestions TEXT,
    raw_ai_result TEXT,
    contract_type VARCHAR(64),
    complexity_level VARCHAR(24),
    key_focus TEXT,
    annotation_result TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_contract_check_record_user_created ON contract_check_record(user_id, created_at DESC);

-- These four tables match Agent V1 and the legacy conversation startup DDL.
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    model VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_session_user_updated ON ai_chat_session(user_id, updated_at DESC);
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES ai_chat_session(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    model VARCHAR(64),
    requested_model VARCHAR(64),
    model_used VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_session_created ON ai_chat_message(session_id, created_at);
CREATE TABLE IF NOT EXISTS ai_chat_attachment (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES ai_chat_message(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(64),
    source_type VARCHAR(64),
    content_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_attachment_message ON ai_chat_attachment(message_id);
CREATE TABLE IF NOT EXISTS user_contract_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contract_name VARCHAR(255) NOT NULL,
    source_code TEXT NOT NULL,
    precheck_conclusion VARCHAR(255),
    risk_level VARCHAR(32),
    issue_count INTEGER DEFAULT 0,
    issues TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_contract_record_user_status_updated ON user_contract_record(user_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_user_contract_record_status ON user_contract_record(status);

-- Match SmartContractMapper's description/source_code ||| queries and pdb.score(id).
CREATE INDEX IF NOT EXISTS idx_smart_contract_bm25 ON smart_contract
    USING bm25 (id, (source_code::pdb.source_code), (description::pdb.jieba))
    WITH (key_field = 'id');
CREATE INDEX IF NOT EXISTS idx_smart_contract_code_hnsw ON smart_contract USING hnsw (code_embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_smart_contract_desc_hnsw ON smart_contract USING hnsw (description_embedding vector_cosine_ops);

CREATE OR REPLACE FUNCTION update_updated_at_column() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.update_time = now();
    RETURN NEW;
END;
$$;
DO $$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['smart_contract', 'user_table', 'group_contract', 'group_table', 'user_favorite']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_trigger
            WHERE tgrelid = table_name::regclass AND tgname = 'contractsearch_update_time') THEN
            EXECUTE format('CREATE TRIGGER contractsearch_update_time BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()', table_name);
        END IF;
    END LOOP;
END;
$$;
COMMIT;
