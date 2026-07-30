package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetCanonicalMoment;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetCanonicalMomentTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetDetails;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;

import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    public List<CanonicalTranscriptContextWindow> findSearchableTranscriptContexts(
            UUID assetId,
            UUID workspaceId,
            List<CanonicalTranscriptContextTarget> targets
    ) {
        if (assetId == null || workspaceId == null || targets == null || targets.isEmpty()) {
            return List.of();
        }
        return assetStore.findById(assetId)
                .filter(asset -> workspaceId.equals(asset.getWorkspaceId()))
                .filter(asset -> asset.getStatus() == AssetStatus.SEARCHABLE)
                .filter(asset -> workspaceQueryApplication.isOwnedByCurrentUser(asset.getWorkspaceId()))
                .map(asset -> transcriptStore.loadContextWindows(asset.getId(), targets))
                .orElseGet(List::of);
    }

    /**
     * Resolves current canonical moments for owned Assets. Targets are grouped by Asset so the
     * lookup stays one bounded query per distinct Asset rather than one per requested row. A
     * missing Asset, a foreign Asset or a transcript row that no longer exists is simply absent
     * from the result; a supplied row identifier never falls back to a different row.
     */
    @Transactional(readOnly = true)
    public List<AssetCanonicalMoment> findAuthorizedCanonicalMoments(List<AssetCanonicalMomentTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        Map<UUID, Set<String>> rowIdsByAsset = new LinkedHashMap<>();
        for (AssetCanonicalMomentTarget target : targets) {
            if (target == null || target.assetId() == null || !StringUtils.hasText(target.transcriptRowId())) {
                continue;
            }
            rowIdsByAsset
                    .computeIfAbsent(target.assetId(), ignored -> new LinkedHashSet<>())
                    .add(target.transcriptRowId());
        }

        List<AssetCanonicalMoment> moments = new ArrayList<>();
        rowIdsByAsset.forEach((assetId, rowIds) -> assetStore.findById(assetId)
                .filter(asset -> workspaceQueryApplication.isOwnedByCurrentUser(asset.getWorkspaceId()))
                .ifPresent(asset -> transcriptStore.loadCanonicalRows(assetId, List.copyOf(rowIds)).stream()
                        .filter(this::isUsable)
                        .forEach(row -> rowIds.stream()
                                .filter(rowId -> matchesTranscriptRowId(row, rowId))
                                .findFirst()
                                .ifPresent(rowId -> moments.add(toCanonicalMoment(asset, rowId, row))))));
        return List.copyOf(moments);
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

    private AssetCanonicalMoment toCanonicalMoment(Asset asset, String requestedRowId, AssetTranscriptRowView row) {
        return new AssetCanonicalMoment(
                asset.getId(),
                asset.getWorkspaceId(),
                asset.getTitle(),
                asset.getSourceType(),
                requestedRowId,
                row.segmentIndex(),
                row.startMs(),
                row.endMs(),
                row.text()
        );
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
