package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;

@Service
public class AssetDeletionService {

    private final AssetRepository assetRepository;
    private final AssetPersistenceService assetPersistenceService;
    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;

    public AssetDeletionService(
            AssetRepository assetRepository,
            AssetPersistenceService assetPersistenceService,
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient,
            ElasticsearchProperties elasticsearchProperties
    ) {
        this.assetRepository = assetRepository;
        this.assetPersistenceService = assetPersistenceService;
        this.elasticsearchRestClient = elasticsearchRestClient;
        this.elasticsearchProperties = elasticsearchProperties;
    }

    public void deleteAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

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

        execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_delete_by_query?refresh=true", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(deleteBody)
                        .retrieve()
                        .toBodilessEntity(),
                "delete transcript documents for asset " + assetId
        );
    }

    private void execute(Runnable operation, String description) {
        try {
            operation.run();
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
}
