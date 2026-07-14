package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.DirectProcessingJobCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.KafkaProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobUpdateCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;
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
    private final AssetTranscriptRowSnapshotRepository assetTranscriptRowSnapshotRepository;
    private final ProcessingRequestApplication processingRequestApplication;

    public AssetPersistenceService(
            AssetRepository assetRepository,
            AssetTranscriptRowSnapshotRepository assetTranscriptRowSnapshotRepository,
            ProcessingRequestApplication processingRequestApplication
    ) {
        this.assetRepository = assetRepository;
        this.assetTranscriptRowSnapshotRepository = assetTranscriptRowSnapshotRepository;
        this.processingRequestApplication = processingRequestApplication;
    }

    @Transactional
    public AssetUploadResponse persistDirectUploadResult(
            UUID assetId,
            String originalFilename,
            String title,
            Workspace workspace,
            StoredObject storedObject,
            DirectProcessingUploadResult directResult
    ) {
        Asset asset = assetRepository.save(new Asset(
                assetId,
                originalFilename,
                title,
                directResult.assetStatus(),
                workspace,
                storedObject.bucket(),
                storedObject.objectKey(),
                storedObject.contentType(),
                storedObject.sizeBytes(),
                storedObject.eTag()
        ));

        ProcessingJobView processingJob = processingRequestApplication.createDirectJob(new DirectProcessingJobCommand(
                asset.getId(),
                directResult.taskId(),
                directResult.videoId(),
                directResult.processingStatus(),
                directResult.rawStatus()
        ));

        return new AssetUploadResponse(asset.getId(), processingJob.id(), asset.getStatus(), asset.getWorkspaceId());
    }

    @Transactional
    public AssetUploadResponse persistKafkaRequestUpload(
            UUID assetId,
            String originalFilename,
            String title,
            Workspace workspace,
            StoredObject storedObject
    ) {
        Asset asset = assetRepository.save(new Asset(
                assetId,
                originalFilename,
                title,
                AssetStatus.PROCESSING,
                workspace,
                storedObject.bucket(),
                storedObject.objectKey(),
                storedObject.contentType(),
                storedObject.sizeBytes(),
                storedObject.eTag()
        ));

        ProcessingJobView processingJob = processingRequestApplication.createKafkaJobAndRequest(
                new KafkaProcessingRequestCommand(
                        asset.getId(),
                        workspace.getId(),
                        workspace.getOwnerId(),
                        storedObject.bucket(),
                        storedObject.objectKey(),
                        asset.getOriginalFilename(),
                        storedObject.contentType(),
                        storedObject.sizeBytes()
                ));

        return new AssetUploadResponse(asset.getId(), processingJob.id(), asset.getStatus(), asset.getWorkspaceId());
    }

    @Transactional
    public AssetStatusResponse refreshAssetStatus(
            Asset asset,
            ProcessingJobView processingJob,
            String rawUpstreamTaskState,
            ProcessingJobStatus updatedProcessingJobStatus,
            AssetStatus updatedAssetStatus
    ) {
        boolean processingJobChanged = false;
        boolean assetStatusChanged = false;

        if (processingJob.status() != updatedProcessingJobStatus) {
            processingJobChanged = true;
        }

        if (asset.getStatus() != updatedAssetStatus) {
            asset.setStatus(updatedAssetStatus);
            assetStatusChanged = true;
        }

        if (!Objects.equals(processingJob.rawUpstreamTaskState(), rawUpstreamTaskState)) {
            processingJobChanged = true;
        }

        if (processingJobChanged) {
            processingJob = processingRequestApplication.updateJob(new ProcessingJobUpdateCommand(
                    processingJob.id(), updatedProcessingJobStatus, rawUpstreamTaskState
            ));
        }

        if (assetStatusChanged) {
            assetRepository.save(asset);
        }

        return new AssetStatusResponse(
                asset.getId(),
                processingJob.id(),
                asset.getStatus(),
                processingJob.status()
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
            List<AssetTranscriptRowInput> transcriptRows
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

        List<AssetTranscriptRowSnapshot> sortedSnapshots = sortTranscriptSnapshots(
                assetTranscriptRowSnapshotRepository.saveAll(snapshots)
        );
        return sortedSnapshots;
    }

    @Transactional
    public void deleteAssetRecords(Asset asset) {
        assetTranscriptRowSnapshotRepository.deleteByAssetId(asset.getId());
        processingRequestApplication.deleteForAsset(asset.getId());
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
