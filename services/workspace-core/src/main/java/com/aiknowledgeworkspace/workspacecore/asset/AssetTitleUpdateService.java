package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AssetTitleUpdateService {

    private static final int MAX_ASSET_TITLE_LENGTH = 255;

    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;

    public AssetTitleUpdateService(
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient,
            ElasticsearchProperties elasticsearchProperties
    ) {
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.elasticsearchRestClient = elasticsearchRestClient;
        this.elasticsearchProperties = elasticsearchProperties;
    }

    public Asset updateAssetTitle(UUID assetId, UpdateAssetTitleRequest request) {
        String normalizedTitle = normalizeTitle(request);
        Asset asset = assetService.getAsset(assetId);

        if (normalizedTitle.equals(asset.getTitle())) {
            return asset;
        }

        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            syncSearchableAssetTitle(assetId, normalizedTitle);
        }

        return assetPersistenceService.updateAssetTitle(asset, normalizedTitle);
    }

    private String normalizeTitle(UpdateAssetTitleRequest request) {
        if (request == null || request.title() == null) {
            throw new InvalidAssetTitleException("title is required");
        }

        String normalizedTitle = request.title().trim();
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new InvalidAssetTitleException("title must not be blank");
        }
        if (normalizedTitle.length() > MAX_ASSET_TITLE_LENGTH) {
            throw new InvalidAssetTitleException(
                    "title must be less than or equal to " + MAX_ASSET_TITLE_LENGTH + " characters"
            );
        }
        return normalizedTitle;
    }

    private void syncSearchableAssetTitle(UUID assetId, String normalizedTitle) {
        Map<String, Object> requestBody = Map.of(
                "script", Map.of(
                        "source", "ctx._source.assetTitle = params.assetTitle",
                        "lang", "painless",
                        "params", Map.of("assetTitle", normalizedTitle)
                ),
                "query", Map.of(
                        "term", Map.of("assetId.keyword", assetId.toString())
                )
        );

        JsonNode responseBody = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_update_by_query?refresh=true", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(JsonNode.class),
                "sync search metadata for asset " + assetId
        );

        validateTitleSyncResponse(assetId, responseBody);
    }

    private void validateTitleSyncResponse(UUID assetId, JsonNode responseBody) {
        if (responseBody == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch title sync response body was empty");
        }

        JsonNode failuresNode = responseBody.path("failures");
        if (!failuresNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch title sync response did not include failures");
        }
        if (!failuresNode.isEmpty()) {
            JsonNode firstFailure = failuresNode.get(0);
            String reason = firstFailure.path("cause").path("reason").asText(null);
            String message = "Elasticsearch title sync failed for asset " + assetId;
            if (StringUtils.hasText(reason)) {
                message = message + ": " + reason;
            }
            throw new ElasticsearchIntegrationException(message);
        }

        long total = readLong(responseBody, "total", "title sync response");
        long updated = readLong(responseBody, "updated", "title sync response");
        long versionConflicts = readLong(responseBody, "version_conflicts", "title sync response");

        if (total <= 0) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch title sync did not match any transcript documents for asset " + assetId
            );
        }
        if (versionConflicts > 0) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch title sync hit " + versionConflicts + " version conflicts for asset " + assetId
            );
        }
        if (updated != total) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch title sync updated only " + updated + " of " + total
                            + " transcript documents for asset " + assetId
            );
        }
    }

    private long readLong(JsonNode responseBody, String fieldName, String description) {
        JsonNode fieldNode = responseBody.path(fieldName);
        if (!fieldNode.canConvertToLong()) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch " + description + " did not include a numeric " + fieldName
            );
        }
        return fieldNode.asLong();
    }

    private <T> T execute(AssetTitleUpdateOperation<T> operation, String description) {
        try {
            return operation.run();
        } catch (ResourceAccessException exception) {
            throw new ElasticsearchConnectivityException(
                    "Elasticsearch is unavailable while trying to " + description,
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch returned HTTP " + exception.getStatusCode().value()
                            + " while trying to " + description,
                    exception
            );
        } catch (RestClientException exception) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch request failed while trying to " + description,
                    exception
            );
        }
    }

    @FunctionalInterface
    private interface AssetTitleUpdateOperation<T> {
        T run();
    }
}
