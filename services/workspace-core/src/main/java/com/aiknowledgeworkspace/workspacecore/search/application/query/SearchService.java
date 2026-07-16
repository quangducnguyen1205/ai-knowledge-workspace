package com.aiknowledgeworkspace.workspacecore.search.application.query;

import com.aiknowledgeworkspace.workspacecore.search.InvalidSearchRequestException;
import com.aiknowledgeworkspace.workspacecore.search.SearchAssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQueryPort;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SearchService {

    private static final int MAX_SEARCHABLE_ASSET_TERMS = 1_000;

    private final WorkspaceQueryApplication workspaceQueryApplication;
    private final SearchAssetQueryPort searchAssetQueryPort;
    private final TranscriptSearchQueryPort transcriptSearchQueryPort;

    public SearchService(
            WorkspaceQueryApplication workspaceQueryApplication,
            SearchAssetQueryPort searchAssetQueryPort,
            TranscriptSearchQueryPort transcriptSearchQueryPort
    ) {
        this.workspaceQueryApplication = workspaceQueryApplication;
        this.searchAssetQueryPort = searchAssetQueryPort;
        this.transcriptSearchQueryPort = transcriptSearchQueryPort;
    }

    public SearchResponse search(String query, UUID workspaceId, UUID assetId) {
        String normalizedQuery = normalizeQuery(query);
        UUID resolvedWorkspaceId = workspaceQueryApplication.resolveWorkspaceId(workspaceId);
        UUID validatedAssetId = validateAssetScope(assetId, resolvedWorkspaceId);
        List<UUID> eligibleAssetIds = resolveEligibleAssetIds(resolvedWorkspaceId, validatedAssetId);
        List<String> meaningfulTerms = SearchRelevancePolicy.meaningfulTerms(normalizedQuery);
        if (eligibleAssetIds.isEmpty() || meaningfulTerms.isEmpty()) {
            return new SearchResponse(normalizedQuery, resolvedWorkspaceId, validatedAssetId, 0, List.of());
        }
        List<TranscriptSearchHit> hits = transcriptSearchQueryPort.search(new TranscriptSearchQuery(
                normalizedQuery, resolvedWorkspaceId, validatedAssetId, eligibleAssetIds, meaningfulTerms
        ));

        List<TranscriptSearchHit> focusedHits = SearchRelevancePolicy.select(
                hits, meaningfulTerms, validatedAssetId == null
        );
        return toSearchResponse(normalizedQuery, resolvedWorkspaceId, validatedAssetId, focusedHits);
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

    private SearchResponse toSearchResponse(
            String query, UUID workspaceId, UUID assetId, List<TranscriptSearchHit> hits
    ) {
        List<SearchResultResponse> results = hits.stream()
                .map(hit -> new SearchResultResponse(
                        hit.assetId(), hit.assetTitle(), hit.transcriptRowId(), hit.segmentIndex(),
                        hit.text(), hit.createdAt(), hit.score()
                ))
                .toList();
        return new SearchResponse(query, workspaceId, assetId, results.size(), results);
    }
}
