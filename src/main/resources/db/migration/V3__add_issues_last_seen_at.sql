ALTER TABLE issues
    ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_issues_last_seen_at ON issues (last_seen_at);
