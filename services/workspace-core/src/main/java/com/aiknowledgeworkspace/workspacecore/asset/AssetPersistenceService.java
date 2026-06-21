package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetPersistenceService {

    private final AssetRepository assetRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final AssetTranscriptRowSnapshotRepository assetTranscriptRowSnapshotRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    public AssetPersistenceService(
            AssetRepository assetRepository,
            ProcessingJobRepository processingJobRepository,
            AssetTranscriptRowSnapshotRepository assetTranscriptRowSnapshotRepository,
            OutboxEventRepository outboxEventRepository,
            OutboxEventFactory outboxEventFactory
    ) {
        this.assetRepository = assetRepository;
        this.processingJobRepository = processingJobRepository;
        this.assetTranscriptRowSnapshotRepository = assetTranscriptRowSnapshotRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
    }

    @Transactional
    public AssetUploadResponse persistUploadResult(
            UUID assetId,
            String originalFilename,
            String title,
            AssetStatus initialAssetStatus,
            ProcessingJobStatus initialProcessingStatus,
            Workspace workspace,
            StoredObject storedObject,
            FastApiUploadResponse upstreamResponse
    ) {
        Asset asset = assetRepository.save(new Asset(
                assetId,
                originalFilename,
                title,
                initialAssetStatus,
                workspace,
                storedObject.bucket(),
                storedObject.objectKey(),
                storedObject.contentType(),
                storedObject.sizeBytes(),
                storedObject.eTag()
        ));

        OutboxEvent processingRequestedEvent = outboxEventFactory.assetProcessingRequested(
                asset,
                workspace,
                storedObject
        );

        ProcessingJob processingJob = new ProcessingJob(
                asset.getId(),
                upstreamResponse.taskId(),
                upstreamResponse.videoId(),
                initialProcessingStatus,
                upstreamResponse.status()
        );
        processingJob.setProcessingRequestEventId(processingRequestedEvent.getId());
        processingJob = processingJobRepository.save(processingJob);

        outboxEventRepository.save(processingRequestedEvent);

        return new AssetUploadResponse(asset.getId(), processingJob.getId(), asset.getStatus(), asset.getWorkspaceId());
    }

    @Transactional
    public AssetStatusResponse refreshAssetStatus(
            Asset asset,
            ProcessingJob processingJob,
            String rawUpstreamTaskState,
            ProcessingJobStatus updatedProcessingJobStatus,
            AssetStatus updatedAssetStatus
    ) {
        boolean processingJobChanged = false;
        boolean assetStatusChanged = false;

        if (processingJob.getProcessingJobStatus() != updatedProcessingJobStatus) {
            processingJob.setProcessingJobStatus(updatedProcessingJobStatus);
            processingJobChanged = true;
        }

        if (asset.getStatus() != updatedAssetStatus) {
            asset.setStatus(updatedAssetStatus);
            assetStatusChanged = true;
        }

        if (!Objects.equals(processingJob.getRawUpstreamTaskState(), rawUpstreamTaskState)) {
            processingJob.setRawUpstreamTaskState(rawUpstreamTaskState);
            processingJobChanged = true;
        }

        if (processingJobChanged) {
            processingJobRepository.save(processingJob);
        }

        if (assetStatusChanged) {
            assetRepository.save(asset);
        }

        return new AssetStatusResponse(
                asset.getId(),
                processingJob.getId(),
                asset.getStatus(),
                processingJob.getProcessingJobStatus()
        );
    }

    @Transactional
    public void updateAssetStatus(Asset asset, AssetStatus updatedAssetStatus) {
        if (asset.getStatus() != updatedAssetStatus) {
            asset.setStatus(updatedAssetStatus);
            assetRepository.save(asset);
        }
    }

    @Transactional
    public Asset updateAssetWorkspace(Asset asset, Workspace workspace) {
        if (!Objects.equals(asset.getWorkspaceId(), workspace.getId())) {
            asset.setWorkspace(workspace);
            asset = assetRepository.save(asset);
        }
        return asset;
    }

    @Transactional
    public Asset updateAssetTitle(Asset asset, String updatedTitle) {
        if (!Objects.equals(asset.getTitle(), updatedTitle)) {
            asset.setTitle(updatedTitle);
            asset = assetRepository.save(asset);
        }
        return asset;
    }

    @Transactional(readOnly = true)
    public List<AssetTranscriptRowSnapshot> loadTranscriptSnapshot(UUID assetId) {
        return sortTranscriptSnapshots(assetTranscriptRowSnapshotRepository.findByAssetId(assetId));
    }

    @Transactional
    public List<AssetTranscriptRowSnapshot> replaceTranscriptSnapshot(
            Asset asset,
            List<FastApiTranscriptRowResponse> transcriptRows
    ) {
        assetTranscriptRowSnapshotRepository.deleteByAssetId(asset.getId());

        List<AssetTranscriptRowSnapshot> snapshots = transcriptRows.stream()
                .map(transcriptRow -> new AssetTranscriptRowSnapshot(
                        asset.getId(),
                        transcriptRow.id(),
                        transcriptRow.videoId(),
                        transcriptRow.segmentIndex(),
                        transcriptRow.text(),
                        transcriptRow.createdAt()
                ))
                .toList();

        return sortTranscriptSnapshots(assetTranscriptRowSnapshotRepository.saveAll(snapshots));
    }

    @Transactional
    public void deleteAssetRecords(Asset asset) {
        assetTranscriptRowSnapshotRepository.deleteByAssetId(asset.getId());
        processingJobRepository.findByAssetId(asset.getId())
                .ifPresent(processingJobRepository::delete);
        assetRepository.delete(asset);
    }

    private List<AssetTranscriptRowSnapshot> sortTranscriptSnapshots(List<AssetTranscriptRowSnapshot> snapshots) {
        return snapshots.stream()
                .sorted(Comparator.comparing(
                        AssetTranscriptRowSnapshot::getSegmentIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }
}
