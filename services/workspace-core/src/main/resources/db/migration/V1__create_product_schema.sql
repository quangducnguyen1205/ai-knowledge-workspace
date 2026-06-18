CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_accounts_email UNIQUE (email)
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    default_workspace BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_workspaces_owner_created
    ON workspaces (owner_id, created_at);

CREATE INDEX idx_workspaces_owner_default
    ON workspaces (owner_id, default_workspace);

CREATE TABLE assets (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    workspace_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_assets_status
        CHECK (status IN ('PROCESSING', 'TRANSCRIPT_READY', 'SEARCHABLE', 'FAILED')),
    CONSTRAINT fk_assets_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
);

CREATE INDEX idx_assets_workspace_created
    ON assets (workspace_id, created_at);

CREATE INDEX idx_assets_workspace_status_created
    ON assets (workspace_id, status, created_at);

CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    fastapi_task_id VARCHAR(128) NOT NULL,
    fastapi_video_id VARCHAR(128) NOT NULL,
    processing_job_status VARCHAR(32) NOT NULL,
    raw_upstream_task_state VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_processing_jobs_asset_id UNIQUE (asset_id),
    CONSTRAINT ck_processing_jobs_status
        CHECK (processing_job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_processing_jobs_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE TABLE asset_transcript_rows (
    snapshot_id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    transcript_row_id VARCHAR(255),
    video_id VARCHAR(128) NOT NULL,
    segment_index INTEGER,
    text TEXT NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    CONSTRAINT fk_asset_transcript_rows_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_asset_transcript_rows_asset_segment
    ON asset_transcript_rows (asset_id, segment_index);

CREATE INDEX idx_asset_transcript_rows_asset_transcript_row
    ON asset_transcript_rows (asset_id, transcript_row_id);
