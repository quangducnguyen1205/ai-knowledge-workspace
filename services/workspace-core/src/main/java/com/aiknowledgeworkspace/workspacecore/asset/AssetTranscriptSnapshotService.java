package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingRequestRow;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetTranscriptSnapshotService {

    private final AssetRepository assetRepository;
    private final AssetPersistenceService assetPersistenceService;
    private final IndexingRequestApplication indexingRequestApplication;

    public AssetTranscriptSnapshotService(
            AssetRepository assetRepository,
            AssetPersistenceService assetPersistenceService,
            IndexingRequestApplication indexingRequestApplication
    ) {
        this.assetRepository = assetRepository;
        this.assetPersistenceService = assetPersistenceService;
        this.indexingRequestApplication = indexingRequestApplication;
    }

    @Transactional
    public List<AssetTranscriptRowSnapshot> replaceCanonicalSnapshot(
            Asset asset,
            List<AssetTranscriptRowInput> transcriptRows
    ) {
        List<AssetTranscriptRowInput> usableRows = usableRows(transcriptRows);
        List<AssetTranscriptRowSnapshot> snapshots = assetPersistenceService.replaceTranscriptSnapshot(
                asset,
                usableRows
        );
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
            assetPersistenceService.updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        }
    }

    @Transactional
    public void markTranscriptReady(Asset asset) {
        AssetStatus updatedStatus = asset.getStatus() == AssetStatus.SEARCHABLE
                ? AssetStatus.SEARCHABLE
                : AssetStatus.TRANSCRIPT_READY;
        assetPersistenceService.updateAssetStatus(asset, updatedStatus);
    }

    @Transactional
    public void markProcessingFailed(UUID assetId) {
        Asset asset = loadAsset(assetId);
        if (asset.getStatus() != AssetStatus.FAILED) {
            assetPersistenceService.updateAssetStatus(asset, AssetStatus.FAILED);
        }
    }

    Asset loadAsset(UUID assetId) {
        return assetRepository.findById(assetId)
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

    private List<IndexingRequestRow> toIndexingRequestRows(List<AssetTranscriptRowSnapshot> snapshots) {
        return snapshots.stream()
                .map(snapshot -> new IndexingRequestRow(
                        snapshot.getTranscriptRowId(),
                        snapshot.getVideoId(),
                        snapshot.getSegmentIndex(),
                        snapshot.getText(),
                        snapshot.getCreatedAt()
                ))
                .toList();
    }
}
