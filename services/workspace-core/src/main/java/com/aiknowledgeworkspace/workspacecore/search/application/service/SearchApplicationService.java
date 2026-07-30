package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.exception.InvalidSearchRequestException;
import com.aiknowledgeworkspace.workspacecore.search.application.exception.SearchAssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.in.SearchQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContext;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextLoadException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextTarget;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SearchApplicationService implements SearchQueryUseCase {

    private static final int MAX_SEARCHABLE_ASSET_TERMS = 1_000;
    private static final int MAX_CONTEXT_TARGETS = 12;
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchApplicationService.class);

    private final WorkspaceAccessUseCase workspaceQueryApplication;
    private final SearchAssetQueryPort searchAssetQueryPort;
    private final TranscriptSearchQueryPort transcriptSearchQueryPort;

    public SearchApplicationService(
            WorkspaceAccessUseCase workspaceQueryApplication,
            SearchAssetQueryPort searchAssetQueryPort,
            TranscriptSearchQueryPort transcriptSearchQueryPort
    ) {
        this.workspaceQueryApplication = workspaceQueryApplication;
        this.searchAssetQueryPort = searchAssetQueryPort;
        this.transcriptSearchQueryPort = transcriptSearchQueryPort;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        String normalizedQuery = normalizeQuery(query == null ? null : query.text());
        UUID resolvedWorkspaceId = workspaceQueryApplication.resolveWorkspaceId(query == null ? null : query.workspaceId());
        UUID validatedAssetId = validateAssetScope(query == null ? null : query.assetId(), resolvedWorkspaceId);
        List<UUID> eligibleAssetIds = resolveEligibleAssetIds(resolvedWorkspaceId, validatedAssetId);
        List<String> meaningfulTerms = SearchRelevancePolicy.meaningfulTerms(normalizedQuery);
        if (eligibleAssetIds.isEmpty() || meaningfulTerms.isEmpty()) {
            return new SearchResult(normalizedQuery, resolvedWorkspaceId, validatedAssetId, List.of());
        }
        List<TranscriptSearchHit> hits = transcriptSearchQueryPort.search(new TranscriptSearchQuery(
                normalizedQuery, resolvedWorkspaceId, validatedAssetId, eligibleAssetIds, meaningfulTerms
        ));
        List<TranscriptSearchHit> authorizedHits = filterAuthorizedHits(
                hits, validatedAssetId, eligibleAssetIds
        );

        List<TranscriptSearchHit> focusedHits = SearchRelevancePolicy.select(
                authorizedHits, meaningfulTerms, validatedAssetId == null
        );
        List<SearchHit> results = query.includeContextSnippet()
                ? hydrateCanonicalContexts(resolvedWorkspaceId, focusedHits)
                : toSearchHits(focusedHits);
        return new SearchResult(normalizedQuery, resolvedWorkspaceId, validatedAssetId, results);
    }

    private List<TranscriptSearchHit> filterAuthorizedHits(
            List<TranscriptSearchHit> hits,
            UUID validatedAssetId,
            List<UUID> eligibleAssetIds
    ) {
        Set<UUID> eligibleAssetIdSet = Set.copyOf(eligibleAssetIds);
        List<TranscriptSearchHit> authorizedHits = hits.stream()
                .filter(hit -> validatedAssetId == null
                        ? eligibleAssetIdSet.contains(hit.assetId())
                        : validatedAssetId.equals(hit.assetId()))
                .toList();

        int discardedCount = hits.size() - authorizedHits.size();
        if (discardedCount > 0) {
            LOGGER.warn(
                    "Discarded {} out-of-scope search hits for {} scope",
                    discardedCount,
                    validatedAssetId == null ? "workspace" : "asset"
            );
        }
        return authorizedHits;
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new InvalidSearchRequestException("INVALID_SEARCH_QUERY", "q query parameter is required");
        }
        return query.trim();
    }

    private UUID validateAssetScope(UUID assetId, UUID workspaceId) {
        if (assetId == null) {
            return null;
        }

        SearchAssetDetails asset = authorizedAssetDetails(assetId);
        if (!workspaceId.equals(asset.workspaceId())) {
            throw new SearchAssetNotFoundException();
        }

        return assetId;
    }

    private List<UUID> resolveEligibleAssetIds(UUID workspaceId, UUID assetId) {
        if (assetId != null) {
            SearchAssetDetails asset = authorizedAssetDetails(assetId);
            return asset.searchable() ? List.of(assetId) : List.of();
        }

        List<UUID> eligibleAssetIds = searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId);
        if (eligibleAssetIds.size() > MAX_SEARCHABLE_ASSET_TERMS) {
            throw new InvalidSearchRequestException(
                    "SEARCH_SCOPE_TOO_LARGE",
                    "Workspace search currently supports up to " + MAX_SEARCHABLE_ASSET_TERMS + " searchable assets"
            );
        }
        return eligibleAssetIds;
    }

    private SearchAssetDetails authorizedAssetDetails(UUID assetId) {
        try {
            return searchAssetQueryPort.getAuthorizedAssetDetails(assetId);
        } catch (SearchAssetUnavailableException exception) {
            throw new SearchAssetNotFoundException();
        }
    }

    private List<SearchHit> hydrateCanonicalContexts(UUID workspaceId, List<TranscriptSearchHit> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        if (hits.size() > MAX_CONTEXT_TARGETS) {
            throw new SearchCanonicalContextLoadException();
        }

        List<SearchCanonicalContextTarget> targets = hits.stream()
                .map(hit -> new SearchCanonicalContextTarget(
                        hit.assetId(), canonicalRowId(hit.transcriptRowId()), hit.segmentIndex()
                ))
                .toList();
        List<SearchCanonicalContext> contexts = searchAssetQueryPort.loadCanonicalContexts(workspaceId, targets);
        Map<ContextKey, SearchCanonicalContext> contextByTarget = indexContexts(contexts, targets);

        List<SearchHit> hydrated = hits.stream()
                .map(hit -> hydrateHit(hit, contextByTarget.get(ContextKey.from(hit))))
                .filter(Objects::nonNull)
                .toList();
        int discardedCount = hits.size() - hydrated.size();
        if (discardedCount > 0) {
            LOGGER.warn("Discarded {} stale canonical search hits during context hydration", discardedCount);
        }
        return hydrated;
    }

    private Map<ContextKey, SearchCanonicalContext> indexContexts(
            List<SearchCanonicalContext> contexts,
            List<SearchCanonicalContextTarget> targets
    ) {
        if (contexts == null || contexts.size() > MAX_CONTEXT_TARGETS) {
            throw new SearchCanonicalContextLoadException();
        }

        Set<ContextKey> requestedKeys = targets.stream()
                .map(ContextKey::from)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<ContextKey, SearchCanonicalContext> indexed = new LinkedHashMap<>();
        int canonicalRowCount = 0;
        for (SearchCanonicalContext context : contexts) {
            if (context == null
                    || context.assetId() == null
                    || context.matchedRow() == null
                    || context.orderedRows().isEmpty()
                    || context.orderedRows().size() > 3) {
                throw new SearchCanonicalContextLoadException();
            }
            canonicalRowCount += context.orderedRows().size();
            boolean containsExactlyOneMatchedRow =
                    context.orderedRows().stream().filter(context.matchedRow()::equals).count() == 1;
            boolean containsInvalidRow = context.orderedRows().stream().anyMatch(row ->
                    row == null
                            || row.segmentIndex() == null
                            || SearchContextSnippetPolicy.normalizeText(row.text()).isEmpty()
            );
            if (canonicalRowCount > MAX_CONTEXT_TARGETS * 3
                    || !containsExactlyOneMatchedRow
                    || containsInvalidRow
                    || !hasStrictCanonicalOrder(context.orderedRows())) {
                throw new SearchCanonicalContextLoadException();
            }
            ContextKey key = ContextKey.from(context);
            if (!requestedKeys.contains(key) || indexed.putIfAbsent(key, context) != null) {
                throw new SearchCanonicalContextLoadException();
            }
        }
        return indexed;
    }

    private boolean hasStrictCanonicalOrder(List<SearchCanonicalContextRow> rows) {
        for (int index = 1; index < rows.size(); index++) {
            if (rows.get(index - 1).segmentIndex() >= rows.get(index).segmentIndex()) {
                return false;
            }
        }
        return true;
    }

    private SearchHit hydrateHit(TranscriptSearchHit hit, SearchCanonicalContext context) {
        if (!matchesCanonicalRow(hit, context)) {
            return null;
        }

        String contextSnippet;
        try {
            contextSnippet = SearchContextSnippetPolicy.format(context);
        } catch (IllegalArgumentException exception) {
            throw new SearchCanonicalContextLoadException();
        }
        return new SearchHit(
                hit.assetId(), hit.assetTitle(), hit.transcriptRowId(), hit.segmentIndex(),
                hit.startMs(), hit.endMs(), hit.text(), contextSnippet, hit.createdAt(), hit.score()
        );
    }

    private boolean matchesCanonicalRow(TranscriptSearchHit hit, SearchCanonicalContext context) {
        if (context == null || !hit.assetId().equals(context.assetId())) {
            return false;
        }
        SearchCanonicalContextRow canonical = context.matchedRow();
        if (StringUtils.hasText(hit.transcriptRowId())) {
            if (!hit.transcriptRowId().equals(canonical.transcriptRowId())) {
                return false;
            }
        } else if (StringUtils.hasText(canonical.transcriptRowId())) {
            return false;
        }
        return Objects.equals(hit.segmentIndex(), canonical.segmentIndex())
                && Objects.equals(hit.startMs(), canonical.startMs())
                && Objects.equals(hit.endMs(), canonical.endMs())
                && Objects.equals(
                SearchContextSnippetPolicy.normalizeText(hit.text()),
                SearchContextSnippetPolicy.normalizeText(canonical.text())
        )
                && Objects.equals(hit.createdAt(), canonical.createdAt());
    }

    private List<SearchHit> toSearchHits(List<TranscriptSearchHit> hits) {
        return hits.stream()
                .map(hit -> new SearchHit(
                        hit.assetId(), hit.assetTitle(), hit.transcriptRowId(), hit.segmentIndex(),
                        hit.startMs(), hit.endMs(), hit.text(), null, hit.createdAt(), hit.score()
                ))
                .toList();
    }

    private static String canonicalRowId(String transcriptRowId) {
        return StringUtils.hasText(transcriptRowId) ? transcriptRowId : null;
    }

    private record ContextKey(UUID assetId, String transcriptRowId, Integer segmentIndex) {

        private static ContextKey from(TranscriptSearchHit hit) {
            return new ContextKey(hit.assetId(), canonicalRowId(hit.transcriptRowId()), hit.segmentIndex());
        }

        private static ContextKey from(SearchCanonicalContext context) {
            return new ContextKey(
                    context.assetId(),
                    canonicalRowId(context.requestedTranscriptRowId()),
                    context.requestedSegmentIndex()
            );
        }

        private static ContextKey from(SearchCanonicalContextTarget target) {
            return new ContextKey(
                    target.assetId(),
                    canonicalRowId(target.transcriptRowId()),
                    target.segmentIndex()
            );
        }
    }
}
