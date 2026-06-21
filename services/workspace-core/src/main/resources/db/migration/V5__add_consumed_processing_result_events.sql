ALTER TABLE processing_jobs
    ADD COLUMN processing_request_event_id UUID;

CREATE INDEX idx_processing_jobs_asset_request_event
    ON processing_jobs (asset_id, processing_request_event_id);

CREATE TABLE consumed_processing_result_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    causation_event_id UUID NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    error_detail VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_consumed_processing_result_events_status
        CHECK (status IN ('RECEIVED', 'APPLIED', 'FAILED'))
);

CREATE INDEX idx_consumed_processing_result_events_status_received
    ON consumed_processing_result_events (status, received_at);

CREATE INDEX idx_consumed_processing_result_events_aggregate
    ON consumed_processing_result_events (aggregate_id, received_at);
