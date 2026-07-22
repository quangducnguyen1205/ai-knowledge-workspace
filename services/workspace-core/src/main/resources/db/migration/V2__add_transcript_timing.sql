ALTER TABLE asset_transcript_rows
    ADD COLUMN start_ms BIGINT;

ALTER TABLE asset_transcript_rows
    ADD COLUMN end_ms BIGINT;

ALTER TABLE asset_transcript_rows
    ADD CONSTRAINT ck_asset_transcript_rows_timing CHECK (
        (start_ms IS NULL AND end_ms IS NULL)
        OR (
            start_ms IS NOT NULL
            AND end_ms IS NOT NULL
            AND start_ms >= 0
            AND end_ms >= start_ms
        )
    );
