package com.aiknowledgeworkspace.workspacecore.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asset_transcript_rows")
public class AssetTranscriptRowSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID snapshotId;

    @Column(nullable = false)
    private UUID assetId;

    @Column(length = 255)
    private String transcriptRowId;

    @Column(nullable = false, length = 128)
    private String videoId;

    @Column
    private Integer segmentIndex;

    @Lob
    @Column(nullable = false)
    private String text;

    @Column(nullable = false, length = 64)
    private String createdAt;

    protected AssetTranscriptRowSnapshot() {
    }

    public AssetTranscriptRowSnapshot(
            UUID assetId,
            String transcriptRowId,
            String videoId,
            Integer segmentIndex,
            String text,
            String createdAt
    ) {
        this.assetId = assetId;
        this.transcriptRowId = transcriptRowId;
        this.videoId = videoId;
        this.segmentIndex = segmentIndex;
        this.text = text;
        this.createdAt = createdAt;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getTranscriptRowId() {
        return transcriptRowId;
    }

    public String getVideoId() {
        return videoId;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public String getText() {
        return text;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
