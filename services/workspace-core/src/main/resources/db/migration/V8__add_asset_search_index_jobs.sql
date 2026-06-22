CREATE TABLE asset_search_index_jobs (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    snapshot_fingerprint VARCHAR(128) NOT NULL,
    request_outbox_event_id UUID,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_asset_search_index_jobs_status
        CHECK (status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT ck_asset_search_index_jobs_attempt_count_non_negative
        CHECK (attempt_count >= 0),
    CONSTRAINT fk_asset_search_index_jobs_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_asset_search_index_jobs_asset_status
    ON asset_search_index_jobs (asset_id, status, created_at);

CREATE INDEX idx_asset_search_index_jobs_asset_fingerprint_status
    ON asset_search_index_jobs (asset_id, snapshot_fingerprint, status);

CREATE INDEX idx_asset_search_index_jobs_request_outbox
    ON asset_search_index_jobs (request_outbox_event_id);
