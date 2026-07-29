CREATE TABLE asset_playback_progress (
    asset_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    position_ms BIGINT NOT NULL,
    completed BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_asset_playback_progress PRIMARY KEY (asset_id, user_id),
    CONSTRAINT ck_asset_playback_progress_position_non_negative CHECK (position_ms >= 0),
    CONSTRAINT fk_asset_playback_progress_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);
