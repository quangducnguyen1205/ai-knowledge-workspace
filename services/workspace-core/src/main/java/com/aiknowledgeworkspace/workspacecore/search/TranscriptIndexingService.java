package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.ProcessingJobNotFoundException;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TranscriptIndexingService {

    private final ProcessingJobRepository processingJobRepository;
    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;
    private final TranscriptIndexDocumentMapper transcriptIndexDocumentMapper;

    public TranscriptIndexingService(
            ProcessingJobRepository processingJobRepository,
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            TranscriptSearchIndexClient transcriptSearchIndexClient,
            TranscriptIndexDocumentMapper transcriptIndexDocumentMapper
    ) {
        this.processingJobRepository = processingJobRepository;
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
        this.transcriptIndexDocumentMapper = transcriptIndexDocumentMapper;
    }

    public AssetIndexResponse indexAssetTranscript(UUID assetId) {
        Asset asset = assetService.getAsset(assetId);
        ProcessingJob processingJob = processingJobRepository.findByAssetId(assetId)
                .orElseThrow(ProcessingJobNotFoundException::new);

        List<AssetTranscriptRowSnapshot> transcriptRows = assetService.loadUsableTranscriptSnapshot(asset, processingJob);

        AssetStatus fallbackStatus = asset.getStatus() == AssetStatus.SEARCHABLE
                ? AssetStatus.SEARCHABLE
                : AssetStatus.TRANSCRIPT_READY;

        try {
            transcriptSearchIndexClient.indexTranscriptRows(toIndexOperations(asset, transcriptRows));
            transcriptSearchIndexClient.refreshTranscriptIndex();
        } catch (ElasticsearchIntegrationException exception) {
            assetPersistenceService.updateAssetStatus(asset, fallbackStatus);
            throw exception;
        }

        assetPersistenceService.updateAssetStatus(asset, AssetStatus.SEARCHABLE);

        return new AssetIndexResponse(asset.getId(), AssetStatus.SEARCHABLE, transcriptRows.size());
    }

    private List<TranscriptSearchIndexClient.TranscriptIndexOperation> toIndexOperations(
            Asset asset,
            List<AssetTranscriptRowSnapshot> transcriptRows
    ) {
        return transcriptRows.stream()
                .map(transcriptRow -> new TranscriptSearchIndexClient.TranscriptIndexOperation(
                        transcriptIndexDocumentMapper.toDocumentId(asset, transcriptRow),
                        transcriptIndexDocumentMapper.toDocument(
                                asset,
                                transcriptRow,
                                AssetStatus.SEARCHABLE
                        )
                ))
                .toList();
    }
}
