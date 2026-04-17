package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
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
public class AssetDeletionService {

    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;

    public AssetDeletionService(
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

    public void deleteAsset(UUID assetId) {
        Asset asset = assetService.getAsset(assetId);

        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            deleteIndexedTranscriptRows(assetId);
        }

        assetPersistenceService.deleteAssetRecords(asset);
    }

    private void deleteIndexedTranscriptRows(UUID assetId) {
        Map<String, Object> deleteBody = Map.of(
                "query", Map.of(
                        "term", Map.of("assetId.keyword", assetId.toString())
                )
        );

        JsonNode responseBody = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_delete_by_query?refresh=true", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(deleteBody)
                        .retrieve()
                        .body(JsonNode.class),
                "delete transcript documents for asset " + assetId
        );

        validateDeleteResponse(assetId, responseBody);
    }

    private void validateDeleteResponse(UUID assetId, JsonNode responseBody) {
        if (responseBody == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch delete response body was empty");
        }

        JsonNode failuresNode = responseBody.path("failures");
        if (!failuresNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch delete response did not include failures");
        }
        if (!failuresNode.isEmpty()) {
            JsonNode firstFailure = failuresNode.get(0);
            String reason = firstFailure.path("cause").path("reason").asText(null);
            String message = "Elasticsearch delete failed for asset " + assetId;
            if (StringUtils.hasText(reason)) {
                message = message + ": " + reason;
            }
            throw new ElasticsearchIntegrationException(message);
        }

        long total = readLong(responseBody, "total", "delete response");
        long deleted = readLong(responseBody, "deleted", "delete response");
        long versionConflicts = readLong(responseBody, "version_conflicts", "delete response");

        if (versionConflicts > 0) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch delete hit " + versionConflicts + " version conflicts for asset " + assetId
            );
        }
        if (deleted != total) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch delete removed only " + deleted + " of " + total
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

    private <T> T execute(AssetDeletionOperation<T> operation, String description) {
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
    private interface AssetDeletionOperation<T> {
        T run();
    }
}
