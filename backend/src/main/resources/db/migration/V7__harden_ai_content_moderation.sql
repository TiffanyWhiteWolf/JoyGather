ALTER TABLE ai_audit_logs
  ADD COLUMN risk_level VARCHAR(16),
  ADD COLUMN confidence DECIMAL(5,4),
  ADD COLUMN provider VARCHAR(60),
  ADD COLUMN model VARCHAR(120),
  ADD COLUMN provider_status VARCHAR(32),
  ADD COLUMN request_snapshot TEXT,
  ADD COLUMN error_message TEXT,
  ADD COLUMN duration_ms INT NOT NULL DEFAULT 0;

CREATE INDEX idx_ai_audit_result_created
  ON ai_audit_logs (result, created_at);
