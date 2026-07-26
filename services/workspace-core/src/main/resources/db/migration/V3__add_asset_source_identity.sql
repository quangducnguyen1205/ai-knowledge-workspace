ALTER TABLE assets
    ADD COLUMN source_type VARCHAR(16);

ALTER TABLE assets
    ADD COLUMN youtube_video_id VARCHAR(128);

UPDATE assets
SET source_type = 'UPLOAD'
WHERE source_type IS NULL;

ALTER TABLE assets
    ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE assets
    ALTER COLUMN original_filename DROP NOT NULL;

ALTER TABLE assets
    ALTER COLUMN storage_bucket DROP NOT NULL;

ALTER TABLE assets
    ALTER COLUMN object_key DROP NOT NULL;

ALTER TABLE assets
    ALTER COLUMN content_type DROP NOT NULL;

ALTER TABLE assets
    ALTER COLUMN size_bytes DROP NOT NULL;

ALTER TABLE assets
    ADD CONSTRAINT ck_assets_source_type CHECK (
        source_type IN ('UPLOAD', 'YOUTUBE')
    );

ALTER TABLE assets
    ADD CONSTRAINT ck_assets_youtube_video_id_valid CHECK (
        youtube_video_id IS NULL
        OR (
            youtube_video_id = TRIM(youtube_video_id)
            AND CHAR_LENGTH(youtube_video_id) BETWEEN 1 AND 128
        )
    );

ALTER TABLE assets
    ADD CONSTRAINT ck_assets_source_shape CHECK (
        (
            source_type = 'UPLOAD'
            AND youtube_video_id IS NULL
            AND original_filename IS NOT NULL
            AND storage_bucket IS NOT NULL
            AND object_key IS NOT NULL
            AND content_type IS NOT NULL
            AND size_bytes IS NOT NULL
            AND size_bytes >= 0
        )
        OR (
            source_type = 'YOUTUBE'
            AND youtube_video_id IS NOT NULL
            AND TRIM(youtube_video_id) <> ''
            AND original_filename IS NULL
            AND storage_bucket IS NULL
            AND object_key IS NULL
            AND content_type IS NULL
            AND size_bytes IS NULL
            AND etag IS NULL
        )
    );

ALTER TABLE assets
    ADD CONSTRAINT uk_assets_workspace_youtube_video UNIQUE (workspace_id, youtube_video_id);
