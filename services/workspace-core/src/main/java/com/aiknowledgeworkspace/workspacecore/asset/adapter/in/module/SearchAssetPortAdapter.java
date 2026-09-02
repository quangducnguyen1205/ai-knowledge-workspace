package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetDetails;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.CanonicalTranscriptContextReadException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContext;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextLoadException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextTarget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchAssetPortAdapter implements IndexingAssetPort, SearchAssetQueryPort {

    private static final int MAX_CONTEXT_TARGETS = 12;

    private final AssetTranscriptQueryService transcriptQueryService;
    private final AssetSearchabilityService assetSearchabilityService;

    public SearchAssetPortAdapter(
            AssetTranscriptQueryService transcriptQueryService,
            AssetSearchabilityService assetSearchabilityService
    ) {
        this.transcriptQueryService = transcriptQueryService;
        this.assetSearchabilityService = assetSearchabilityService;
    }

    @Override
    public Optional<IndexingAssetSource> findCurrentIndexingSource(UUID assetId) {
        return transcriptQueryService.findCurrentIndexingSource(assetId).map(this::toSource);
    }

    @Override
    public List<UUID> findProjectionSourceAssetIds(UUID afterAssetId, int limit) {
        return transcriptQueryService.findAssetIdsWithCanonicalTranscript(afterAssetId, limit);
    }

    @Override
    public IndexingAssetSource loadAuthorizedIndexingSource(UUID assetId) {
        try {
            AssetDetails details = transcriptQueryService.getAuthorizedAssetDetails(assetId);
            return toSource(new AssetIndexingSource(
                    details.assetId(),
                    details.workspaceId(),
                    details.title(),
                    transcriptQueryService.loadUsableSnapshot(assetId)
            ));
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public void markTranscriptReady(UUID assetId) {
        try {
            assetSearchabilityService.markTranscriptReady(assetId);
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public void markSearchable(UUID assetId) {
        try {
            assetSearchabilityService.markSearchable(assetId);
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public SearchAssetDetails getAuthorizedAssetDetails(UUID assetId) {
        try {
            AssetDetails details = transcriptQueryService.getAuthorizedAssetDetails(assetId);
            return new SearchAssetDetails(details.assetId(), details.workspaceId(), details.searchable());
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId) {
        return transcriptQueryService.findSearchableAssetIdsInWorkspace(workspaceId);
    }

    @Override
    public List<SearchCanonicalContext> loadCanonicalContexts(
            UUID workspaceId,
            List<SearchCanonicalContextTarget> targets
    ) {
        List<SearchCanonicalContextTarget> distinctTargets = validateAndCoalesce(workspaceId, targets);
        Map<UUID, List<SearchCanonicalContextTarget>> targetsByAsset = new LinkedHashMap<>();
        for (SearchCanonicalContextTarget target : distinctTargets) {
            targetsByAsset.computeIfAbsent(target.assetId(), ignored -> new ArrayList<>()).add(target);
        }

        List<SearchCanonicalContext> contexts = new ArrayList<>();
        try {
            targetsByAsset.forEach((assetId, assetTargets) -> contexts.addAll(
                    loadAssetContexts(assetId, workspaceId, assetTargets)
            ));
        } catch (CanonicalTranscriptContextReadException exception) {
            throw new SearchCanonicalContextLoadException();
        }

        Map<SearchCanonicalContextTarget, SearchCanonicalContext> contextByTarget = new LinkedHashMap<>();
        for (SearchCanonicalContext context : contexts) {
            SearchCanonicalContextTarget key = new SearchCanonicalContextTarget(
                    context.assetId(),
                    context.requestedTranscriptRowId(),
                    context.requestedSegmentIndex()
            );
            if (!distinctTargets.contains(key) || contextByTarget.putIfAbsent(key, context) != null) {
                throw new SearchCanonicalContextLoadException();
            }
        }
        return distinctTargets.stream()
                .map(contextByTarget::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<SearchCanonicalContextTarget> validateAndCoalesce(
            UUID workspaceId,
            List<SearchCanonicalContextTarget> targets
    ) {
        if (workspaceId == null || targets == null || targets.size() > MAX_CONTEXT_TARGETS) {
            throw new SearchCanonicalContextLoadException();
        }

        Set<SearchCanonicalContextTarget> distinctTargets = new LinkedHashSet<>();
        for (SearchCanonicalContextTarget target : targets) {
            if (target == null
                    || target.assetId() == null
                    || (!StringUtils.hasText(target.transcriptRowId()) && target.segmentIndex() == null)) {
                throw new SearchCanonicalContextLoadException();
            }
            distinctTargets.add(target);
        }
        return List.copyOf(distinctTargets);
    }

    private List<SearchCanonicalContext> loadAssetContexts(
            UUID assetId,
            UUID workspaceId,
            List<SearchCanonicalContextTarget> targets
    ) {
        List<CanonicalTranscriptContextTarget> assetTargets = targets.stream()
                .map(target -> new CanonicalTranscriptContextTarget(
                        StringUtils.hasText(target.transcriptRowId()) ? target.transcriptRowId() : null,
                        target.segmentIndex()
                ))
                .toList();
        List<CanonicalTranscriptContextWindow> windows =
                transcriptQueryService.findSearchableTranscriptContexts(assetId, workspaceId, assetTargets);
        return windows.stream()
                .map(window -> toSearchContext(assetId, window))
                .toList();
    }

    private SearchCanonicalContext toSearchContext(
            UUID assetId,
            CanonicalTranscriptContextWindow window
    ) {
        if (window == null || window.matchedRow() == null || window.orderedRows() == null) {
            throw new SearchCanonicalContextLoadException();
        }
        return new SearchCanonicalContext(
                assetId,
                window.requestedTranscriptRowId(),
                window.requestedSegmentIndex(),
                toSearchRow(window.matchedRow()),
                window.orderedRows().stream().map(this::toSearchRow).toList()
        );
    }

    private SearchCanonicalContextRow toSearchRow(CanonicalTranscriptContextRow row) {
        if (row == null || row.segmentIndex() == null || !StringUtils.hasText(row.text())) {
            throw new SearchCanonicalContextLoadException();
        }
        return new SearchCanonicalContextRow(
                row.transcriptRowId(),
                row.segmentIndex(),
                row.startMs(),
                row.endMs(),
                row.text(),
                row.createdAt()
        );
    }

    private IndexingAssetSource toSource(AssetIndexingSource source) {
        return new IndexingAssetSource(
                source.assetId(),
                source.workspaceId(),
                source.assetTitle(),
                source.transcriptRows().stream().map(row -> new IndexingTranscriptRow(
                        row.id(), row.videoId(), row.segmentIndex(), row.startMs(), row.endMs(),
                        row.text(), row.createdAt()
                )).toList()
        );
    }
}
