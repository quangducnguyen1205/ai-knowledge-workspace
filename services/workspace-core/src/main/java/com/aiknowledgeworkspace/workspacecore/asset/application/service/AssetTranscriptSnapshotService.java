package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;

import com.aiknowledgeworkspace.workspacecore.search.api.IndexingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.search.api.IndexingRequestRow;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetTranscriptSnapshotService {

    private final AssetStore assetStore;
    private final CanonicalTranscriptStore transcriptStore;
    private final IndexingRequestUseCase indexingRequestApplication;

    public AssetTranscriptSnapshotService(
            AssetStore assetStore,
            CanonicalTranscriptStore transcriptStore,
            IndexingRequestUseCase indexingRequestApplication
    ) {
        this.assetStore = assetStore;
        this.transcriptStore = transcriptStore;
        this.indexingRequestApplication = indexingRequestApplication;
    }

    @Transactional
    public List<AssetTranscriptRowView> replaceCanonicalSnapshot(
            Asset asset,
            List<AssetTranscriptRowInput> transcriptRows
    ) {
        List<AssetTranscriptRowInput> usableRows = usableRows(transcriptRows);
        List<AssetTranscriptRowView> snapshots = transcriptStore.replace(asset.getId(), usableRows);
        indexingRequestApplication.requestIndexingIfEnabled(asset.getId(), toIndexingRequestRows(snapshots));
        return snapshots;
    }

    @Transactional
    public void applySuccessfulProcessingResult(
            Asset asset,
            List<AssetTranscriptRowInput> transcriptRows
    ) {
        replaceCanonicalSnapshot(asset, transcriptRows);
        if (asset.getStatus() != AssetStatus.SEARCHABLE) {
            updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        }
    }

    @Transactional
    public void markTranscriptReady(Asset asset) {
        AssetStatus updatedStatus = asset.getStatus() == AssetStatus.SEARCHABLE
                ? AssetStatus.SEARCHABLE
                : AssetStatus.TRANSCRIPT_READY;
        updateAssetStatus(asset, updatedStatus);
    }

    @Transactional
    public void markProcessingFailed(UUID assetId) {
        Asset asset = loadAsset(assetId);
        if (asset.getStatus() != AssetStatus.FAILED) {
            updateAssetStatus(asset, AssetStatus.FAILED);
        }
    }

    public Asset loadAsset(UUID assetId) {
        return assetStore.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
    }

    private List<AssetTranscriptRowInput> usableRows(List<AssetTranscriptRowInput> transcriptRows) {
        List<AssetTranscriptRowInput> usableRows = transcriptRows == null
                ? List.of()
                : transcriptRows.stream().filter(this::isUsable).toList();
        if (usableRows.isEmpty()) {
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_USABLE",
                    "Transcript is empty or unusable for this asset"
            );
        }
        return usableRows;
    }

    private boolean isUsable(AssetTranscriptRowInput row) {
        return row != null && row.segmentIndex() != null && StringUtils.hasText(row.text());
    }

    private List<IndexingRequestRow> toIndexingRequestRows(List<AssetTranscriptRowView> snapshots) {
        return snapshots.stream()
                .map(snapshot -> new IndexingRequestRow(
                        snapshot.id(),
                        snapshot.videoId(),
                        snapshot.segmentIndex(),
                        snapshot.startMs(),
                        snapshot.endMs(),
                        snapshot.text(),
                        snapshot.createdAt()
                ))
                .toList();
    }

    private void updateAssetStatus(Asset asset, AssetStatus status) {
        if (asset.getStatus() != status) {
            asset.setStatus(status);
            assetStore.save(asset);
        }
    }
}
