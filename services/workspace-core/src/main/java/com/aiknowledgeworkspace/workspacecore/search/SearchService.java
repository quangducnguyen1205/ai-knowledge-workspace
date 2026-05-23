package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SearchService {

    private final WorkspaceService workspaceService;
    private final AssetService assetService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;

    public SearchService(
            WorkspaceService workspaceService,
            AssetService assetService,
            TranscriptSearchIndexClient transcriptSearchIndexClient
    ) {
        this.workspaceService = workspaceService;
        this.assetService = assetService;
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
    }

    public SearchResponse search(String query, UUID workspaceId, UUID assetId) {
        String normalizedQuery = normalizeQuery(query);
        UUID resolvedWorkspaceId = workspaceService.resolveWorkspaceOrDefault(workspaceId).getId();
        UUID validatedAssetId = validateAssetScope(assetId, resolvedWorkspaceId);
        JsonNode responseBody = transcriptSearchIndexClient.searchTranscriptRows(
                normalizedQuery,
                resolvedWorkspaceId,
                validatedAssetId
        );

        return toSearchResponse(normalizedQuery, resolvedWorkspaceId, validatedAssetId, responseBody);
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

        Asset asset = assetService.getAsset(assetId);
        if (!workspaceId.equals(asset.getWorkspaceId())) {
            throw new AssetNotFoundException();
        }

        return assetId;
    }

    private SearchResponse toSearchResponse(String query, UUID workspaceId, UUID assetId, JsonNode responseBody) {
        if (responseBody == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch search response body was empty");
        }

        JsonNode hitsNode = responseBody.path("hits").path("hits");
        if (!hitsNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch search response did not include hits");
        }

        List<SearchResultResponse> results = new ArrayList<>();
        for (JsonNode hitNode : hitsNode) {
            JsonNode sourceNode = hitNode.path("_source");
            if (!sourceNode.isObject()) {
                throw new ElasticsearchIntegrationException("Elasticsearch search hit did not include _source");
            }

            results.add(new SearchResultResponse(
                    parseAssetId(sourceNode),
                    readText(sourceNode, "assetTitle"),
                    readText(sourceNode, "transcriptRowId"),
                    readInteger(sourceNode, "segmentIndex"),
                    readText(sourceNode, "text"),
                    readText(sourceNode, "createdAt"),
                    readScore(hitNode)
            ));
        }

        return new SearchResponse(query, workspaceId, assetId, results.size(), results);
    }

    private UUID parseAssetId(JsonNode sourceNode) {
        String assetId = readText(sourceNode, "assetId");
        if (!StringUtils.hasText(assetId)) {
            throw new ElasticsearchIntegrationException("Elasticsearch search hit did not include assetId");
        }

        try {
            return UUID.fromString(assetId);
        } catch (IllegalArgumentException exception) {
            throw new ElasticsearchIntegrationException("Elasticsearch search hit included an invalid assetId", exception);
        }
    }

    private String readText(JsonNode sourceNode, String fieldName) {
        JsonNode fieldNode = sourceNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private Integer readInteger(JsonNode sourceNode, String fieldName) {
        JsonNode fieldNode = sourceNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isInt() || !fieldNode.canConvertToInt()) {
            throw new ElasticsearchIntegrationException("Elasticsearch search hit included a non-numeric value for " + fieldName);
        }
        return fieldNode.asInt();
    }

    private Double readScore(JsonNode hitNode) {
        JsonNode scoreNode = hitNode.path("_score");
        if (scoreNode.isMissingNode() || scoreNode.isNull()) {
            return null;
        }
        return scoreNode.asDouble();
    }

}
