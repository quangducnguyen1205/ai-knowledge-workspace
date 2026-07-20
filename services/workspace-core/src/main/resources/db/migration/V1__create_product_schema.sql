CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    identity_provider VARCHAR(255),
    external_subject VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT uk_user_accounts_external_identity UNIQUE (identity_provider, external_subject),
    CONSTRAINT ck_user_accounts_external_identity_pair CHECK (
        (identity_provider IS NULL AND external_subject IS NULL)
        OR (identity_provider IS NOT NULL AND external_subject IS NOT NULL)
    )
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
    storage_bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    etag VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_assets_storage_object UNIQUE (storage_bucket, object_key),
    CONSTRAINT ck_assets_status CHECK (
        status IN ('PROCESSING', 'TRANSCRIPT_READY', 'SEARCHABLE', 'FAILED')
    ),
    CONSTRAINT ck_assets_size_bytes_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT fk_assets_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
);

CREATE INDEX idx_assets_workspace_created
    ON assets (workspace_id, created_at);

CREATE INDEX idx_assets_workspace_status_created
    ON assets (workspace_id, status, created_at);

CREATE INDEX idx_assets_storage_bucket
    ON assets (storage_bucket);

CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    processing_job_status VARCHAR(32) NOT NULL,
    raw_upstream_task_state VARCHAR(64),
    processing_request_event_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_processing_jobs_asset_id UNIQUE (asset_id),
    CONSTRAINT uk_processing_jobs_request_event UNIQUE (processing_request_event_id),
    CONSTRAINT ck_processing_jobs_status CHECK (
        processing_job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT fk_processing_jobs_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_processing_jobs_asset_request_event
    ON processing_jobs (asset_id, processing_request_event_id);

CREATE TABLE asset_transcript_rows (
    snapshot_id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    transcript_row_id VARCHAR(255),
    video_id VARCHAR(128) NOT NULL,
    segment_index INTEGER,
    text TEXT NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    CONSTRAINT uk_asset_transcript_rows_asset_segment UNIQUE (asset_id, segment_index),
    CONSTRAINT uk_asset_transcript_rows_asset_row_id UNIQUE (asset_id, transcript_row_id),
    CONSTRAINT fk_asset_transcript_rows_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_asset_transcript_rows_asset_segment
    ON asset_transcript_rows (asset_id, segment_index);

CREATE INDEX idx_asset_transcript_rows_asset_transcript_row
    ON asset_transcript_rows (asset_id, transcript_row_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    failure_disposition VARCHAR(32),
    recovery_cycle_count INTEGER NOT NULL DEFAULT 0,
    next_recovery_at TIMESTAMP WITH TIME ZONE,
    last_failure_category VARCHAR(128),
    recovery_exhausted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_outbox_events_status CHECK (
        status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_outbox_events_attempt_count_non_negative CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_events_event_version_positive CHECK (event_version > 0),
    CONSTRAINT ck_outbox_events_failure_disposition CHECK (
        failure_disposition IS NULL
        OR failure_disposition IN ('TRANSIENT', 'PERMANENT', 'UNKNOWN', 'RECOVERY_EXHAUSTED')
    ),
    CONSTRAINT ck_outbox_events_recovery_cycle_count_non_negative CHECK (recovery_cycle_count >= 0)
);

CREATE INDEX idx_outbox_events_status_next_attempt
    ON outbox_events (status, next_attempt_at, created_at);

CREATE INDEX idx_outbox_events_type_status_next_attempt
    ON outbox_events (event_type, status, next_attempt_at, created_at);

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at);

CREATE INDEX idx_outbox_events_recovery_eligibility
    ON outbox_events (status, failure_disposition, next_recovery_at, recovery_cycle_count, created_at);

CREATE TABLE consumed_processing_result_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    causation_event_id UUID NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    error_detail VARCHAR(1024),
    recoverable_event_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_consumed_processing_result_events_status CHECK (
        status IN ('RECEIVED', 'APPLIED', 'FAILED')
    )
);

CREATE INDEX idx_consumed_processing_result_events_status_received
    ON consumed_processing_result_events (status, received_at);

CREATE INDEX idx_consumed_processing_result_events_aggregate
    ON consumed_processing_result_events (aggregate_id, received_at);

CREATE TABLE asset_search_index_jobs (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    snapshot_fingerprint VARCHAR(128) NOT NULL,
    active_fingerprint_key VARCHAR(128),
    request_outbox_event_id UUID,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_asset_search_index_jobs_status CHECK (
        status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_asset_search_index_jobs_attempt_count_non_negative CHECK (attempt_count >= 0),
    CONSTRAINT fk_asset_search_index_jobs_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_asset_search_index_jobs_active_fingerprint
    ON asset_search_index_jobs (asset_id, active_fingerprint_key);

CREATE INDEX idx_asset_search_index_jobs_asset_status
    ON asset_search_index_jobs (asset_id, status, created_at);

CREATE INDEX idx_asset_search_index_jobs_asset_fingerprint_status
    ON asset_search_index_jobs (asset_id, snapshot_fingerprint, status);

CREATE INDEX idx_asset_search_index_jobs_request_outbox
    ON asset_search_index_jobs (request_outbox_event_id);
