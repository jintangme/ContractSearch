-- Phase 1 additive migration. Existing tables are retained and normalized with
-- idempotent CREATE statements so the same migration supports fresh databases
-- and production schemas baselined at version 0.

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    model VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_session_user_updated
    ON ai_chat_session(user_id, updated_at DESC);

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
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_session_created
    ON ai_chat_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS ai_chat_attachment (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES ai_chat_message(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(64),
    source_type VARCHAR(64),
    content_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_attachment_message
    ON ai_chat_attachment(message_id);

CREATE TABLE IF NOT EXISTS user_contract_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contract_name VARCHAR(255) NOT NULL,
    source_code TEXT NOT NULL,
    precheck_conclusion VARCHAR(255),
    risk_level VARCHAR(32),
    issue_count INT DEFAULT 0,
    issues TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_contract_record_user_status_updated
    ON user_contract_record(user_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_user_contract_record_status
    ON user_contract_record(status);

CREATE TABLE IF NOT EXISTS agent_session (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    workspace_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_session_user_updated
    ON agent_session(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_run (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES agent_session(id) ON DELETE SET NULL,
    user_id BIGINT NOT NULL,
    task TEXT NOT NULL,
    model_profile VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    terminal_code VARCHAR(64),
    answer TEXT,
    trace_id VARCHAR(64) NOT NULL,
    step_count INT NOT NULL DEFAULT 0,
    tool_call_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_run_state CHECK (state IN ('RECEIVED','PLAN','TOOL_CALL','OBSERVE','SYNTHESIZE','COMPLETE','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_agent_run_user_created ON agent_run(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_run_session_created ON agent_run(session_id, created_at);

CREATE TABLE IF NOT EXISTS agent_event (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_event_run_sequence UNIQUE(run_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS agent_evidence (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
    source_type VARCHAR(64) NOT NULL,
    source_ref TEXT NOT NULL,
    title TEXT,
    excerpt TEXT,
    trust_level VARCHAR(32) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_evidence_run ON agent_evidence(run_id);

CREATE TABLE IF NOT EXISTS agent_proposal (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
    proposal_kind VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    approved_by BIGINT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_agent_proposal_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXECUTED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_agent_proposal_run_status ON agent_proposal(run_id, status);
