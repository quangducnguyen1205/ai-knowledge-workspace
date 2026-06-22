ALTER TABLE asset_search_index_jobs
    ADD COLUMN active_fingerprint_key VARCHAR(128);

UPDATE asset_search_index_jobs
SET active_fingerprint_key = snapshot_fingerprint
WHERE status IN ('PENDING', 'INDEXING');

CREATE UNIQUE INDEX uk_asset_search_index_jobs_active_fingerprint
    ON asset_search_index_jobs (asset_id, active_fingerprint_key);
