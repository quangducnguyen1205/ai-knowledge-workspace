package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.InvalidFastApiResponseException;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final FastApiProcessingClient fastApiProcessingClient;

    public AssetService(
            AssetRepository assetRepository,
            ProcessingJobRepository processingJobRepository,
            FastApiProcessingClient fastApiProcessingClient
    ) {
        this.assetRepository = assetRepository;
        this.processingJobRepository = processingJobRepository;
        this.fastApiProcessingClient = fastApiProcessingClient;
    }

    public Asset getAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    @Transactional
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

        ProcessingJobStatus initialProcessingStatus = mapInitialProcessingStatus(upstreamResponse.status());
        AssetStatus initialAssetStatus = initialProcessingStatus == ProcessingJobStatus.FAILED
                ? AssetStatus.FAILED
                : AssetStatus.PROCESSING;

        Asset asset = assetRepository.save(new Asset(originalFilename, title, initialAssetStatus));

        ProcessingJob processingJob = new ProcessingJob(
                asset.getId(),
                upstreamResponse.taskId(),
                upstreamResponse.videoId(),
                initialProcessingStatus,
                upstreamResponse.status()
        );
        processingJob = processingJobRepository.save(processingJob);

        // TODO: replace the implicit default workspace assumption with real workspace persistence.
        // TODO: decide how to reconcile orphaned upstream tasks if FastAPI accepts upload but DB persistence fails.

        return new AssetUploadResponse(asset.getId(), processingJob.getId(), asset.getStatus());
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

    private String resolveOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename)) {
            return originalFilename;
        }
        return "upload.bin";
    }

    private String resolveTitle(String requestedTitle, String originalFilename) {
        if (StringUtils.hasText(requestedTitle)) {
            return requestedTitle.trim();
        }
        return originalFilename;
    }

    private ProcessingJobStatus mapInitialProcessingStatus(String upstreamStatus) {
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
}
