CREATE TABLE issues (
    id                BIGSERIAL PRIMARY KEY,
    github_id         BIGINT NOT NULL,
    repository_id     BIGINT NOT NULL,
    number            INTEGER NOT NULL,
    title             VARCHAR(500) NOT NULL,
    html_url          VARCHAR(512) NOT NULL,
    state             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    body_snippet      TEXT,
    labels            TEXT[] NOT NULL DEFAULT '{}',
    comments_count    INTEGER NOT NULL DEFAULT 0,
    github_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    github_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issues_github_id UNIQUE (github_id),
    CONSTRAINT fk_issues_repository FOREIGN KEY (repository_id) REFERENCES repositories (id) ON DELETE CASCADE
);

CREATE INDEX idx_issues_repository_id ON issues (repository_id);
CREATE INDEX idx_issues_state ON issues (state);
CREATE INDEX idx_issues_github_created_at ON issues (github_created_at DESC);
CREATE INDEX idx_issues_labels ON issues USING GIN (labels);
