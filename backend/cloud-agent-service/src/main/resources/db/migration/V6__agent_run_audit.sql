ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS failure_message TEXT,
    ADD COLUMN IF NOT EXISTS retrieval_round_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(96);

CREATE INDEX IF NOT EXISTS idx_agent_event_run_type
    ON agent_event(run_id, event_type, sequence_no);

CREATE INDEX IF NOT EXISTS idx_agent_evidence_source_ref
    ON agent_evidence(source_ref);
