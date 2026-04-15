package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import java.util.List;
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

@Service
public class TranscriptIndexingService {

    private static final MediaType ELASTICSEARCH_BULK_MEDIA_TYPE =
            MediaType.parseMediaType("application/x-ndjson");

    private final ProcessingJobRepository processingJobRepository;
    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;
    private final TranscriptIndexDocumentMapper transcriptIndexDocumentMapper;
    private final ObjectMapper objectMapper;

    public TranscriptIndexingService(
            ProcessingJobRepository processingJobRepository,
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient,
            ElasticsearchProperties elasticsearchProperties,
            TranscriptIndexDocumentMapper transcriptIndexDocumentMapper,
            ObjectMapper objectMapper
    ) {
        this.processingJobRepository = processingJobRepository;
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.elasticsearchRestClient = elasticsearchRestClient;
        this.elasticsearchProperties = elasticsearchProperties;
        this.transcriptIndexDocumentMapper = transcriptIndexDocumentMapper;
        this.objectMapper = objectMapper;
    }

    public AssetIndexResponse indexAssetTranscript(UUID assetId) {
        Asset asset = assetService.getAsset(assetId);
        ProcessingJob processingJob = processingJobRepository.findByAssetId(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processing job not found"));

        List<FastApiTranscriptRowResponse> transcriptRows = assetService.loadUsableTranscriptRows(asset, processingJob);

        AssetStatus fallbackStatus = asset.getStatus() == AssetStatus.SEARCHABLE
                ? AssetStatus.SEARCHABLE
                : AssetStatus.TRANSCRIPT_READY;

        try {
            bulkIndexTranscriptRows(asset, transcriptRows);
            refreshTranscriptIndex();
        } catch (ElasticsearchIntegrationException exception) {
            assetPersistenceService.updateAssetStatus(asset, fallbackStatus);
            throw exception;
        }

        assetPersistenceService.updateAssetStatus(asset, AssetStatus.SEARCHABLE);

        return new AssetIndexResponse(asset.getId(), AssetStatus.SEARCHABLE, transcriptRows.size());
    }

    private void bulkIndexTranscriptRows(Asset asset, List<FastApiTranscriptRowResponse> transcriptRows) {
        String bulkRequestBody = buildBulkRequestBody(asset, transcriptRows);
        JsonNode bulkResponse = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_bulk", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(ELASTICSEARCH_BULK_MEDIA_TYPE)
                        .body(bulkRequestBody)
                        .retrieve()
                        .body(JsonNode.class),
                "bulk index transcript rows for asset " + asset.getId()
        );
        validateBulkResponse(bulkResponse);
    }

    private String buildBulkRequestBody(Asset asset, List<FastApiTranscriptRowResponse> transcriptRows) {
        StringBuilder body = new StringBuilder();

        try {
            for (FastApiTranscriptRowResponse transcriptRow : transcriptRows) {
                String documentId = transcriptIndexDocumentMapper.toDocumentId(asset, transcriptRow);
                TranscriptIndexDocument document = transcriptIndexDocumentMapper.toDocument(
                        asset,
                        transcriptRow,
                        AssetStatus.SEARCHABLE
                );
                body.append(objectMapper.writeValueAsString(Map.of("index", Map.of("_id", documentId))))
                        .append('\n');
                body.append(objectMapper.writeValueAsString(document))
                        .append('\n');
            }
        } catch (JsonProcessingException exception) {
            throw new ElasticsearchIntegrationException("Failed to serialize Elasticsearch bulk request", exception);
        }

        return body.toString();
    }

    private void validateBulkResponse(JsonNode bulkResponse) {
        if (bulkResponse == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch bulk indexing response body was empty");
        }

        JsonNode itemsNode = bulkResponse.path("items");
        if (!itemsNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch bulk indexing response did not include items");
        }

        for (JsonNode itemNode : itemsNode) {
            JsonNode indexNode = itemNode.path("index");
            if (!indexNode.isObject()) {
                throw new ElasticsearchIntegrationException(
                        "Elasticsearch bulk indexing response item did not include index metadata"
                );
            }

            JsonNode statusNode = indexNode.path("status");
            int status = statusNode.canConvertToInt() ? statusNode.asInt() : -1;
            if (status < 200 || status >= 300 || indexNode.hasNonNull("error")) {
                String documentId = indexNode.path("_id").asText("unknown");
                String errorReason = indexNode.path("error").path("reason").asText(null);
                String message = "Elasticsearch bulk indexing failed for document " + documentId
                        + " with status " + status;
                if (errorReason != null && !errorReason.isBlank()) {
                    message = message + ": " + errorReason;
                }
                throw new ElasticsearchIntegrationException(message);
            }
        }
    }

    private void refreshTranscriptIndex() {
        execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_refresh", elasticsearchProperties.getTranscriptIndexName())
                        .retrieve()
                        .toBodilessEntity(),
                "refresh transcript index"
        );
    }

    private <T> T execute(IndexOperation<T> operation, String description) {
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
    private interface IndexOperation<T> {
        T run();
    }
}
