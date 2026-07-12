ALTER TABLE outbox_events ADD COLUMN failure_disposition VARCHAR(32);
ALTER TABLE outbox_events ADD COLUMN recovery_cycle_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_recovery_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN last_failure_category VARCHAR(128);
ALTER TABLE outbox_events ADD COLUMN recovery_exhausted_at TIMESTAMP WITH TIME ZONE;

UPDATE outbox_events
SET failure_disposition = 'UNKNOWN',
    last_failure_category = 'HISTORICAL_UNCLASSIFIED',
    last_error = 'HISTORICAL_UNCLASSIFIED'
WHERE status = 'FAILED'
  AND failure_disposition IS NULL;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_failure_disposition
        CHECK (failure_disposition IS NULL OR failure_disposition IN ('TRANSIENT', 'PERMANENT', 'UNKNOWN', 'RECOVERY_EXHAUSTED'));

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_recovery_cycle_count_non_negative
        CHECK (recovery_cycle_count >= 0);

CREATE INDEX idx_outbox_events_recovery_eligibility
    ON outbox_events (status, failure_disposition, next_recovery_at, recovery_cycle_count, created_at);
