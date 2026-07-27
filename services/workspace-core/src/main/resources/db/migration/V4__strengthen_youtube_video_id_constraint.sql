ALTER TABLE assets
    DROP CONSTRAINT ck_assets_youtube_video_id_valid;

ALTER TABLE assets
    ADD CONSTRAINT ck_assets_youtube_video_id_valid CHECK (
        youtube_video_id IS NULL
        OR (
            CHAR_LENGTH(youtube_video_id) BETWEEN 1 AND 128
            AND youtube_video_id ~ '^[A-Za-z0-9_-]+$'
        )
    );
