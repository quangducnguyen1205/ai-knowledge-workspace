package com.aiknowledgeworkspace.workspacecore.asset;

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
public class AssetTranscriptQueryService {

    private final AssetRepository assetRepository;
    private final AssetPersistenceService assetPersistenceService;
    private final WorkspaceService workspaceService;

    public AssetTranscriptQueryService(
            AssetRepository assetRepository,
            AssetPersistenceService assetPersistenceService,
            WorkspaceService workspaceService
    ) {
        this.assetRepository = assetRepository;
        this.assetPersistenceService = assetPersistenceService;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public AssetDetails getAuthorizedAssetDetails(UUID assetId) {
        return toDetails(loadAuthorizedAsset(assetId));
    }

    @Transactional(readOnly = true)
    public List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId) {
        return assetRepository.findByWorkspace_IdAndStatus(workspaceId, AssetStatus.SEARCHABLE, Sort.unsorted())
                .stream()
                .map(Asset::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AssetIndexingSource> findCurrentIndexingSource(UUID assetId) {
        return assetRepository.findById(assetId)
                .map(asset -> createIndexingSource(asset, loadUsableTranscriptRows(asset.getId())));
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

    @Transactional(readOnly = true)
    public List<AssetTranscriptRowSnapshot> loadUsableSnapshot(UUID assetId) {
        return assetPersistenceService.loadTranscriptSnapshot(assetId).stream()
                .filter(this::isUsable)
                .sorted(Comparator.comparing(
                        AssetTranscriptRowSnapshot::getSegmentIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Asset loadAuthorizedAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
        if (asset.getWorkspace() == null || !workspaceService.isOwnedByCurrentUser(asset.getWorkspace())) {
            throw new AssetNotFoundException();
        }
        return asset;
    }

    AssetIndexingSource toIndexingSource(Asset asset, List<AssetTranscriptRowSnapshot> snapshots) {
        return createIndexingSource(asset, snapshots.stream().map(this::toView).toList());
    }

    private Optional<AssetTranscriptContext> toTranscriptContext(Asset asset, String transcriptRowId, int window) {
        List<AssetTranscriptRowView> sortedRows = loadUsableTranscriptRows(asset.getId());
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

    private List<AssetTranscriptRowView> loadUsableTranscriptRows(UUID assetId) {
        return loadUsableSnapshot(assetId).stream().map(this::toView).toList();
    }

    private AssetIndexingSource createIndexingSource(Asset asset, List<AssetTranscriptRowView> transcriptRows) {
        return new AssetIndexingSource(asset.getId(), asset.getWorkspaceId(), asset.getTitle(), transcriptRows);
    }

    private AssetDetails toDetails(Asset asset) {
        return new AssetDetails(asset.getId(), asset.getWorkspaceId(), asset.getTitle(), asset.getStatus());
    }

    private AssetTranscriptRowView toView(AssetTranscriptRowSnapshot row) {
        return new AssetTranscriptRowView(
                row.getTranscriptRowId(), row.getVideoId(), row.getSegmentIndex(), row.getText(), row.getCreatedAt()
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
        return row.segmentIndex() != null && ("segment-" + row.segmentIndex()).equals(transcriptRowId);
    }

    private boolean isUsable(AssetTranscriptRowSnapshot row) {
        return row.getSegmentIndex() != null && StringUtils.hasText(row.getText());
    }
}
