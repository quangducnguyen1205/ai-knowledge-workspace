package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import java.util.List;
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

    private final ProcessingJobRepository processingJobRepository;
    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;
    private final TranscriptIndexDocumentMapper transcriptIndexDocumentMapper;

    public TranscriptIndexingService(
            ProcessingJobRepository processingJobRepository,
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient,
            ElasticsearchProperties elasticsearchProperties,
            TranscriptIndexDocumentMapper transcriptIndexDocumentMapper
    ) {
        this.processingJobRepository = processingJobRepository;
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.elasticsearchRestClient = elasticsearchRestClient;
        this.elasticsearchProperties = elasticsearchProperties;
        this.transcriptIndexDocumentMapper = transcriptIndexDocumentMapper;
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
            for (FastApiTranscriptRowResponse transcriptRow : transcriptRows) {
                indexTranscriptRow(asset, transcriptRow);
            }
            refreshTranscriptIndex();
        } catch (ElasticsearchIntegrationException exception) {
            assetPersistenceService.updateAssetStatus(asset, fallbackStatus);
            throw exception;
        }

        assetPersistenceService.updateAssetStatus(asset, AssetStatus.SEARCHABLE);

        // TODO: if indexing volume grows, replace per-document writes with a bulk indexing path.
        return new AssetIndexResponse(asset.getId(), AssetStatus.SEARCHABLE, transcriptRows.size());
    }

    private void indexTranscriptRow(Asset asset, FastApiTranscriptRowResponse transcriptRow) {
        String documentId = transcriptIndexDocumentMapper.toDocumentId(asset, transcriptRow);
        TranscriptIndexDocument document = transcriptIndexDocumentMapper.toDocument(
                asset,
                transcriptRow,
                AssetStatus.SEARCHABLE
        );

        execute(
                () -> elasticsearchRestClient.put()
                        .uri("/{indexName}/_doc/{documentId}",
                                elasticsearchProperties.getTranscriptIndexName(),
                                documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(document)
                        .retrieve()
                        .toBodilessEntity(),
                "index transcript row " + documentId
        );
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
