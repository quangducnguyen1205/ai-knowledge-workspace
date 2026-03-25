package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTaskStatusResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.InvalidFastApiResponseException;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final FastApiProcessingClient fastApiProcessingClient;
    private final AssetPersistenceService assetPersistenceService;

    public AssetService(
            AssetRepository assetRepository,
            ProcessingJobRepository processingJobRepository,
            FastApiProcessingClient fastApiProcessingClient,
            AssetPersistenceService assetPersistenceService
    ) {
        this.assetRepository = assetRepository;
        this.processingJobRepository = processingJobRepository;
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.assetPersistenceService = assetPersistenceService;
    }

    public Asset getAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    public AssetStatusResponse getAssetStatus(UUID assetId) {
        Asset asset = getAsset(assetId);
        ProcessingJob processingJob = processingJobRepository.findByAssetId(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Asset is missing its processing job"
                ));

        if (isTerminal(processingJob.getProcessingJobStatus())) {
            return new AssetStatusResponse(
                    asset.getId(),
                    processingJob.getId(),
                    asset.getStatus(),
                    processingJob.getProcessingJobStatus()
            );
        }

        FastApiTaskStatusResponse upstreamTaskStatus = fastApiProcessingClient.getTaskStatus(processingJob.getFastapiTaskId());
        validateUpstreamTaskStatusResponse(upstreamTaskStatus);

        ProcessingJobStatus updatedProcessingJobStatus = mapUpstreamTaskStatus(upstreamTaskStatus.status());
        AssetStatus updatedAssetStatus = mapAssetStatusFromTaskStatus(updatedProcessingJobStatus);

        // TODO: when transcript fetch is added, use transcript presence to move an asset from
        // TODO: PROCESSING to TRANSCRIPT_READY instead of relying on task success alone.

        return assetPersistenceService.refreshAssetStatus(
                asset,
                processingJob,
                upstreamTaskStatus.status(),
                updatedProcessingJobStatus,
                updatedAssetStatus
        );
    }

    public AssetUploadResponse uploadAsset(MultipartFile file, String requestedTitle) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty file is required");
        }

        String originalFilename = resolveOriginalFilename(file);
        String title = resolveTitle(requestedTitle, originalFilename);

        FastApiUploadResponse upstreamResponse = fastApiProcessingClient.uploadVideo(
                file.getResource(),
                originalFilename,
                title
        );

        validateUpstreamUploadResponse(upstreamResponse);

        ProcessingJobStatus initialProcessingStatus = mapUpstreamTaskStatus(upstreamResponse.status());
        AssetStatus initialAssetStatus = initialProcessingStatus == ProcessingJobStatus.FAILED
                ? AssetStatus.FAILED
                : AssetStatus.PROCESSING;

        // TODO: replace the implicit default workspace assumption with real workspace persistence.
        // TODO: decide how to reconcile orphaned upstream tasks if FastAPI accepts upload but DB persistence fails.


        return assetPersistenceService.persistUploadResult(
                originalFilename,
                title,
                initialAssetStatus,
                initialProcessingStatus,
                upstreamResponse
        );
    }


    private void validateUpstreamUploadResponse(FastApiUploadResponse upstreamResponse) {
        if (upstreamResponse == null) {
            throw new InvalidFastApiResponseException("FastAPI upload response body was empty");
        }
        if (!StringUtils.hasText(upstreamResponse.taskId())) {
            throw new InvalidFastApiResponseException("FastAPI upload response did not include task_id");
        }
        if (!StringUtils.hasText(upstreamResponse.videoId())) {
            throw new InvalidFastApiResponseException("FastAPI upload response did not include video_id");
        }
    }

    private void validateUpstreamTaskStatusResponse(FastApiTaskStatusResponse upstreamResponse) {
        if (upstreamResponse == null) {
            throw new InvalidFastApiResponseException("FastAPI task status response body was empty");
        }
        if (!StringUtils.hasText(upstreamResponse.status())) {
            throw new InvalidFastApiResponseException("FastAPI task status response did not include status");
        }
    }

    private String resolveOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        // Strip any path information that might be included by the client
        String cleanedFilename = StringUtils.getFilename(originalFilename);

        // Fallback to a safe default if nothing usable is provided
        if (!StringUtils.hasText(cleanedFilename)) {
            return "upload.bin";
        }

        // Enforce a maximum length (matches typical @Column(length = 255) constraints)
        final int MAX_FILENAME_LENGTH = 255;
        if (cleanedFilename.length() <= MAX_FILENAME_LENGTH) {
            return cleanedFilename;
        }

        // Try to preserve the file extension when truncating
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < originalFilename.length() - 1) {
            String extension = originalFilename.substring(lastDotIndex);
            int truncatedLength = MAX_FILENAME_LENGTH - extension.length();
            if (truncatedLength > 0) {
                return cleanedFilename.substring(0, truncatedLength) + extension;
            }
        }
        return cleanedFilename.substring(0, MAX_FILENAME_LENGTH);
    }

    private String resolveTitle(String requestedTitle, String originalFilename) {
        String title;
        if (StringUtils.hasText(requestedTitle)) {
            title = requestedTitle.trim();
        } else {
            title = originalFilename;
        }
        int maxLength = 255;
        if (title.length() > maxLength) {
            title = title.substring(0, maxLength);
        }
        return title;
    }

    private ProcessingJobStatus mapUpstreamTaskStatus(String upstreamStatus) {
        if (!StringUtils.hasText(upstreamStatus)) {
            return ProcessingJobStatus.PENDING;
        }

        String normalized = upstreamStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pending", "queued", "created" -> ProcessingJobStatus.PENDING;
            case "running", "processing", "started", "in_progress" -> ProcessingJobStatus.RUNNING;
            case "success", "succeeded", "completed", "complete", "ready" -> ProcessingJobStatus.SUCCEEDED;
            case "failed", "error" -> ProcessingJobStatus.FAILED;
            default -> ProcessingJobStatus.RUNNING;
        };
    }

    private AssetStatus mapAssetStatusFromTaskStatus(ProcessingJobStatus processingJobStatus) {
        return switch (processingJobStatus) {
            case FAILED -> AssetStatus.FAILED;
            case PENDING, RUNNING, SUCCEEDED -> AssetStatus.PROCESSING;
        };
    }

    private boolean isTerminal(ProcessingJobStatus processingJobStatus) {
        return processingJobStatus == ProcessingJobStatus.SUCCEEDED
                || processingJobStatus == ProcessingJobStatus.FAILED;
    }
}
