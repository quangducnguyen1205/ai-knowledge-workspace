ALTER TABLE assets
    ADD COLUMN storage_bucket VARCHAR(255) NOT NULL;

ALTER TABLE assets
    ADD COLUMN object_key VARCHAR(1024) NOT NULL;

ALTER TABLE assets
    ADD COLUMN content_type VARCHAR(255) NOT NULL;

ALTER TABLE assets
    ADD COLUMN size_bytes BIGINT NOT NULL;

ALTER TABLE assets
    ADD COLUMN etag VARCHAR(255);

ALTER TABLE assets
    ADD CONSTRAINT ck_assets_size_bytes_non_negative
        CHECK (size_bytes >= 0);

ALTER TABLE assets
    ADD CONSTRAINT uk_assets_storage_object
        UNIQUE (storage_bucket, object_key);

CREATE INDEX idx_assets_storage_bucket
    ON assets (storage_bucket);
