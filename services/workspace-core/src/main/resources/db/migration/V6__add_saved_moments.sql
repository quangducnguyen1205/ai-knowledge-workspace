CREATE TABLE saved_moments (
    id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    workspace_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    transcript_row_id VARCHAR(255) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_saved_moments PRIMARY KEY (id),
    CONSTRAINT uk_saved_moments_user_asset_row UNIQUE (user_id, asset_id, transcript_row_id),
    CONSTRAINT ck_saved_moments_transcript_row_id_not_blank CHECK (TRIM(transcript_row_id) <> ''),
    CONSTRAINT fk_saved_moments_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_saved_moments_user_workspace_recent
    ON saved_moments (user_id, workspace_id, saved_at DESC, id DESC);
