package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTaskStatusResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.InvalidFastApiResponseException;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
class DirectProcessingCompatibilityAdapter {

    private final FastApiProcessingClient fastApiProcessingClient;
    private final AssetTranscriptQueryService transcriptQueryService;
    private final AssetTranscriptSnapshotService transcriptSnapshotService;

    DirectProcessingCompatibilityAdapter(
            FastApiProcessingClient fastApiProcessingClient,
            AssetTranscriptQueryService transcriptQueryService,
            AssetTranscriptSnapshotService transcriptSnapshotService
    ) {
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.transcriptQueryService = transcriptQueryService;
        this.transcriptSnapshotService = transcriptSnapshotService;
    }

    DirectProcessingUploadResult upload(Resource resource, String originalFilename, String title) {
        FastApiUploadResponse response = fastApiProcessingClient.uploadVideo(resource, originalFilename, title);
        validateUploadResponse(response);
        ProcessingJobStatus processingStatus = mapTaskStatus(response.status());
        AssetStatus assetStatus = processingStatus == ProcessingJobStatus.FAILED
                ? AssetStatus.FAILED
                : AssetStatus.PROCESSING;
        return new DirectProcessingUploadResult(
                response.taskId(), response.videoId(), response.status(), processingStatus, assetStatus
        );
    }

    DirectProcessingTaskState taskState(String taskId) {
        FastApiTaskStatusResponse response = fastApiProcessingClient.getTaskStatus(taskId);
        validateTaskStatusResponse(response);
        ProcessingJobStatus processingStatus = mapTaskStatus(response.status());
        return new DirectProcessingTaskState(
                response.status(),
                processingStatus,
                mapAssetStatus(processingStatus)
        );
    }

    List<AssetTranscriptRowSnapshot> loadOrCaptureTranscript(Asset asset, ProcessingJobView processingJob) {
        if (processingJob.status() != ProcessingJobStatus.SUCCEEDED) {
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_READY",
                    "Transcript is not ready until processing reaches terminal success"
            );
        }
        return loadOrCaptureTranscript(asset, processingJob.fastapiVideoId());
    }

    @Transactional
    public AssetIndexingSource loadAuthorizedIndexingSourceForCompletedProcessing(UUID assetId, String videoId) {
        Asset asset = transcriptQueryService.loadAuthorizedAsset(assetId);
        List<AssetTranscriptRowSnapshot> rows = loadOrCaptureTranscript(asset, videoId);
        return transcriptQueryService.toIndexingSource(asset, rows);
    }

    private List<AssetTranscriptRowSnapshot> loadOrCaptureTranscript(Asset asset, String videoId) {
        List<AssetTranscriptRowSnapshot> transcriptRows = transcriptQueryService.loadUsableSnapshot(asset.getId());
        if (transcriptRows.isEmpty()) {
            transcriptRows = captureTranscript(asset, videoId);
        }
        transcriptSnapshotService.markTranscriptReady(asset);
        return transcriptRows;
    }

    private List<AssetTranscriptRowSnapshot> captureTranscript(Asset asset, String videoId) {
        List<AssetTranscriptRowInput> rows = fastApiProcessingClient.getTranscript(videoId).stream()
                .map(row -> row == null ? null : toInput(row))
                .toList();
        try {
            return transcriptSnapshotService.replaceCanonicalSnapshot(asset, rows);
        } catch (TranscriptUnavailableException exception) {
            transcriptSnapshotService.markProcessingFailed(asset.getId());
            throw exception;
        }
    }

    private void validateUploadResponse(FastApiUploadResponse response) {
        if (response == null) {
            throw new InvalidFastApiResponseException("FastAPI upload response body was empty");
        }
        if (!StringUtils.hasText(response.taskId())) {
            throw new InvalidFastApiResponseException("FastAPI upload response did not include task_id");
        }
        if (!StringUtils.hasText(response.videoId())) {
            throw new InvalidFastApiResponseException("FastAPI upload response did not include video_id");
        }
    }

    private void validateTaskStatusResponse(FastApiTaskStatusResponse response) {
        if (response == null) {
            throw new InvalidFastApiResponseException("FastAPI task status response body was empty");
        }
        if (!StringUtils.hasText(response.status())) {
            throw new InvalidFastApiResponseException("FastAPI task status response did not include status");
        }
    }

    private ProcessingJobStatus mapTaskStatus(String upstreamStatus) {
        if (!StringUtils.hasText(upstreamStatus)) {
            return ProcessingJobStatus.PENDING;
        }
        return switch (upstreamStatus.trim().toLowerCase(Locale.ROOT)) {
            case "pending", "queued", "created" -> ProcessingJobStatus.PENDING;
            case "running", "processing", "started", "in_progress" -> ProcessingJobStatus.RUNNING;
            case "success", "succeeded", "completed", "complete", "ready" -> ProcessingJobStatus.SUCCEEDED;
            case "failed", "error" -> ProcessingJobStatus.FAILED;
            default -> ProcessingJobStatus.RUNNING;
        };
    }

    private AssetStatus mapAssetStatus(ProcessingJobStatus processingStatus) {
        return switch (processingStatus) {
            case FAILED -> AssetStatus.FAILED;
            case PENDING, RUNNING, SUCCEEDED -> AssetStatus.PROCESSING;
        };
    }

    private AssetTranscriptRowInput toInput(FastApiTranscriptRowResponse row) {
        return new AssetTranscriptRowInput(
                row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
        );
    }
}
