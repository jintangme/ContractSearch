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
  ON user_contract_record (user_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_user_contract_record_status
  ON user_contract_record (status);
