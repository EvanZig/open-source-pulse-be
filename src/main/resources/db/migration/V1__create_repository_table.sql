CREATE TABLE repositories (
    id BIGSERIAL PRIMARY KEY,
    github_id BIGINT NOT NULL UNIQUE,
    owner VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    html_url VARCHAR(512) NOT NULL,
    description TEXT,
    stars BIGINT NOT NULL DEFAULT 0,
    language VARCHAR(50),
    CONSTRAINT uk_repositories_github_id UNIQUE (github_id),
    CONSTRAINT uk_repositories_owner_name UNIQUE (owner, name)
)