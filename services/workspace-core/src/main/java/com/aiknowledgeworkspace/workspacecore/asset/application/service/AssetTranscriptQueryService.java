package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetDetails;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;

import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssetTranscriptQueryService {

    private final AssetStore assetStore;
    private final CanonicalTranscriptStore transcriptStore;
    private final WorkspaceAccessUseCase workspaceQueryApplication;

    public AssetTranscriptQueryService(
            AssetStore assetStore,
            CanonicalTranscriptStore transcriptStore,
            WorkspaceAccessUseCase workspaceQueryApplication
    ) {
        this.assetStore = assetStore;
        this.transcriptStore = transcriptStore;
        this.workspaceQueryApplication = workspaceQueryApplication;
    }

    @Transactional(readOnly = true)
    public AssetDetails getAuthorizedAssetDetails(UUID assetId) {
        return toDetails(loadAuthorizedAsset(assetId));
    }

    @Transactional(readOnly = true)
    public List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId) {
        return assetStore.findByWorkspaceId(workspaceId)
                .stream()
                .filter(asset -> asset.getStatus() == AssetStatus.SEARCHABLE)
                .map(Asset::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AssetIndexingSource> findCurrentIndexingSource(UUID assetId) {
        return assetStore.findById(assetId)
                .map(asset -> createIndexingSource(asset, loadUsableTranscriptRows(asset.getId())));
    }

    @Transactional(readOnly = true)
    public Optional<AssetTranscriptContext> findSearchableTranscriptContext(
            UUID assetId,
            UUID workspaceId,
            String transcriptRowId,
            int window
    ) {
        return assetStore.findById(assetId)
                .filter(asset -> workspaceId.equals(asset.getWorkspaceId()))
                .filter(asset -> asset.getStatus() == AssetStatus.SEARCHABLE)
                .flatMap(asset -> toTranscriptContext(asset, transcriptRowId, window));
    }

    @Transactional(readOnly = true)
    public List<AssetTranscriptRowView> loadUsableSnapshot(UUID assetId) {
        return transcriptStore.load(assetId).stream()
                .filter(this::isUsable)
                .sorted(Comparator.comparing(
                        AssetTranscriptRowView::segmentIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Asset loadAuthorizedAsset(UUID assetId) {
        Asset asset = assetStore.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
        if (!workspaceQueryApplication.isOwnedByCurrentUser(asset.getWorkspaceId())) {
            throw new AssetNotFoundException();
        }
        return asset;
    }

    public AssetIndexingSource toIndexingSource(Asset asset, List<AssetTranscriptRowView> rows) {
        return createIndexingSource(asset, rows);
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
        return loadUsableSnapshot(assetId);
    }

    private AssetIndexingSource createIndexingSource(Asset asset, List<AssetTranscriptRowView> transcriptRows) {
        return new AssetIndexingSource(asset.getId(), asset.getWorkspaceId(), asset.getTitle(), transcriptRows);
    }

    private AssetDetails toDetails(Asset asset) {
        return new AssetDetails(asset.getId(), asset.getWorkspaceId(), asset.getTitle(), asset.getStatus());
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

    private boolean isUsable(AssetTranscriptRowView row) {
        return row.segmentIndex() != null && StringUtils.hasText(row.text());
    }
}
