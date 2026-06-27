package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetReadService {

    private final AssetRepository assetRepository;
    private final AssetPersistenceService assetPersistenceService;
    private final FastApiProcessingClient fastApiProcessingClient;
    private final WorkspaceService workspaceService;

    public AssetReadService(
            AssetRepository assetRepository,
            AssetPersistenceService assetPersistenceService,
            FastApiProcessingClient fastApiProcessingClient,
            WorkspaceService workspaceService
    ) {
        this.assetRepository = assetRepository;
        this.assetPersistenceService = assetPersistenceService;
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public AssetDetails getAuthorizedAssetDetails(UUID assetId) {
        return toDetails(loadAuthorizedAsset(assetId));
    }

    @Transactional(readOnly = true)
    public List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId) {
        return assetRepository.findByWorkspace_IdAndStatus(
                        workspaceId,
                        AssetStatus.SEARCHABLE,
                        Sort.unsorted()
                ).stream()
                .map(Asset::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AssetIndexingSource> findCurrentIndexingSource(UUID assetId) {
        return assetRepository.findById(assetId)
                .map(asset -> toIndexingSource(asset, loadUsablePersistedTranscriptSnapshot(asset.getId())));
    }

    @Transactional
    public AssetIndexingSource loadAuthorizedIndexingSourceForCompletedProcessing(
            UUID assetId,
            String fallbackVideoId
    ) {
        Asset asset = loadAuthorizedAsset(assetId);
        List<AssetTranscriptRowView> transcriptRows = loadUsablePersistedTranscriptSnapshot(asset.getId());
        if (transcriptRows.isEmpty()) {
            transcriptRows = captureUsableTranscriptSnapshot(asset, fallbackVideoId);
        }
        markAssetTranscriptUsable(asset);
        return toIndexingSource(asset, transcriptRows);
    }

    @Transactional(readOnly = true)
    public Optional<AssetTranscriptContext> findSearchableTranscriptContext(
            UUID assetId,
            UUID workspaceId,
            String transcriptRowId,
            int window
    ) {
        return assetRepository.findById(assetId)
                .filter(asset -> workspaceId.equals(asset.getWorkspaceId()))
                .filter(asset -> asset.getStatus() == AssetStatus.SEARCHABLE)
                .flatMap(asset -> toTranscriptContext(asset, transcriptRowId, window));
    }

    private Optional<AssetTranscriptContext> toTranscriptContext(Asset asset, String transcriptRowId, int window) {
        List<AssetTranscriptRowView> sortedRows = loadUsablePersistedTranscriptSnapshot(asset.getId());
        int hitRowIndex = findTranscriptRowIndex(sortedRows, transcriptRowId);
        if (hitRowIndex < 0) {
            return Optional.empty();
        }

        AssetTranscriptRowView hitRow = sortedRows.get(hitRowIndex);
        int startIndex = window == 0 ? hitRowIndex : Math.max(0, hitRowIndex - window);
        int endIndexExclusive = window == 0
                ? hitRowIndex + 1
                : Math.min(sortedRows.size(), hitRowIndex + window + 1);
        List<AssetTranscriptRowView> contextRows = new ArrayList<>(
                sortedRows.subList(startIndex, endIndexExclusive)
        );

        return Optional.of(new AssetTranscriptContext(
                asset.getId(),
                asset.getTitle(),
                transcriptRowId,
                hitRow.segmentIndex(),
                window,
                contextRows
        ));
    }

    private Asset loadAuthorizedAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
        if (asset.getWorkspace() == null || !workspaceService.isOwnedByCurrentUser(asset.getWorkspace())) {
            throw new AssetNotFoundException();
        }
        return asset;
    }

    private List<AssetTranscriptRowView> loadUsablePersistedTranscriptSnapshot(UUID assetId) {
        return assetPersistenceService.loadTranscriptSnapshot(assetId).stream()
                .filter(this::isUsableTranscriptSnapshot)
                .map(this::toView)
                .toList();
    }

    private List<AssetTranscriptRowView> captureUsableTranscriptSnapshot(Asset asset, String fallbackVideoId) {
        List<AssetTranscriptRowInput> usableTranscriptRows = fastApiProcessingClient.getTranscript(fallbackVideoId).stream()
                .filter(this::isUsableTranscriptRow)
                .map(this::toInput)
                .toList();

        if (usableTranscriptRows.isEmpty()) {
            assetPersistenceService.updateAssetStatus(asset, AssetStatus.FAILED);
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_USABLE",
                    "Transcript is empty or unusable for this asset"
            );
        }

        return assetPersistenceService.replaceTranscriptSnapshot(asset, usableTranscriptRows).stream()
                .map(this::toView)
                .toList();
    }

    private void markAssetTranscriptUsable(Asset asset) {
        AssetStatus updatedAssetStatus = asset.getStatus() == AssetStatus.SEARCHABLE
                ? AssetStatus.SEARCHABLE
                : AssetStatus.TRANSCRIPT_READY;
        assetPersistenceService.updateAssetStatus(asset, updatedAssetStatus);
    }

    private AssetIndexingSource toIndexingSource(Asset asset, List<AssetTranscriptRowView> transcriptRows) {
        return new AssetIndexingSource(
                asset.getId(),
                asset.getWorkspaceId(),
                asset.getTitle(),
                transcriptRows
        );
    }

    private AssetDetails toDetails(Asset asset) {
        return new AssetDetails(
                asset.getId(),
                asset.getWorkspaceId(),
                asset.getTitle(),
                asset.getStatus()
        );
    }

    private AssetTranscriptRowInput toInput(FastApiTranscriptRowResponse transcriptRow) {
        return new AssetTranscriptRowInput(
                transcriptRow.id(),
                transcriptRow.videoId(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt()
        );
    }

    private AssetTranscriptRowView toView(AssetTranscriptRowSnapshot transcriptRow) {
        return new AssetTranscriptRowView(
                transcriptRow.getTranscriptRowId(),
                transcriptRow.getVideoId(),
                transcriptRow.getSegmentIndex(),
                transcriptRow.getText(),
                transcriptRow.getCreatedAt()
        );
    }

    private int findTranscriptRowIndex(List<AssetTranscriptRowView> rows, String transcriptRowId) {
        for (int index = 0; index < rows.size(); index++) {
            if (matchesTranscriptRowId(rows.get(index), transcriptRowId)) {
                return index;
            }
        }
        return -1;
    }

    private boolean matchesTranscriptRowId(AssetTranscriptRowView row, String transcriptRowId) {
        if (StringUtils.hasText(row.id())) {
            return row.id().equals(transcriptRowId);
        }
        return row.segmentIndex() != null
                && ("segment-" + row.segmentIndex()).equals(transcriptRowId);
    }

    private boolean isUsableTranscriptSnapshot(AssetTranscriptRowSnapshot transcriptRow) {
        return transcriptRow.getSegmentIndex() != null && StringUtils.hasText(transcriptRow.getText());
    }

    private boolean isUsableTranscriptRow(FastApiTranscriptRowResponse transcriptRow) {
        return transcriptRow != null
                && transcriptRow.segmentIndex() != null
                && StringUtils.hasText(transcriptRow.text());
    }
}
