package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTaskStatusResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.InvalidFastApiResponseException;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetService {

    private static final Sort ASSET_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.asc("id")
    );
    private static final int DEFAULT_TRANSCRIPT_CONTEXT_WINDOW = 2;
    private static final int MAX_TRANSCRIPT_CONTEXT_WINDOW = 5;

    private final AssetRepository assetRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final FastApiProcessingClient fastApiProcessingClient;
    private final AssetPersistenceService assetPersistenceService;
    private final WorkspaceService workspaceService;

    public AssetService(
            AssetRepository assetRepository,
            ProcessingJobRepository processingJobRepository,
            FastApiProcessingClient fastApiProcessingClient,
            AssetPersistenceService assetPersistenceService,
            WorkspaceService workspaceService
    ) {
        this.assetRepository = assetRepository;
        this.processingJobRepository = processingJobRepository;
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.assetPersistenceService = assetPersistenceService;
        this.workspaceService = workspaceService;
    }

    public Asset getAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        if (asset.getWorkspace() != null) {
            return asset;
        }

        Workspace defaultWorkspace = workspaceService.ensureDefaultWorkspace();
        return assetPersistenceService.updateAssetWorkspace(asset, defaultWorkspace);
    }

    public List<AssetSummaryResponse> listAssets(UUID workspaceId) {
        Workspace resolvedWorkspace = workspaceService.resolveWorkspaceOrDefault(workspaceId);
        List<Asset> assets = loadAssetsForWorkspace(resolvedWorkspace);

        return assets.stream()
                .map(asset -> backfillWorkspaceIfNeeded(asset, resolvedWorkspace))
                .map(this::toAssetSummaryResponse)
                .toList();
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

        // TODO: if status reads should reflect transcript usability automatically,
        // TODO: check transcript presence here before moving beyond PROCESSING.

        return assetPersistenceService.refreshAssetStatus(
                asset,
                processingJob,
                upstreamTaskStatus.status(),
                updatedProcessingJobStatus,
                updatedAssetStatus
        );
    }

    public List<AssetTranscriptRowResponse> getAssetTranscript(UUID assetId) {
        Asset asset = getAsset(assetId);
        ProcessingJob processingJob = processingJobRepository.findByAssetId(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processing job not found"));

        return loadUsableTranscriptRows(asset, processingJob).stream()
                .map(this::toAssetTranscriptRowResponse)
                .toList();
    }

    public AssetTranscriptContextResponse getAssetTranscriptContext(
            UUID assetId,
            String transcriptRowId,
            Integer window
    ) {
        int resolvedWindow = resolveTranscriptContextWindow(window);
        List<AssetTranscriptRowResponse> sortedRows = new ArrayList<>(getAssetTranscript(assetId));
        sortedRows.sort(Comparator.comparing(
                AssetTranscriptRowResponse::segmentIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));
        int hitRowIndex = findTranscriptRowIndex(sortedRows, transcriptRowId);
        if (hitRowIndex < 0) {
            throw new TranscriptRowNotFoundException(assetId, transcriptRowId);
        }

        AssetTranscriptRowResponse hitRow = sortedRows.get(hitRowIndex);
        int startIndex = Math.max(0, hitRowIndex - resolvedWindow);
        int endIndexExclusive = Math.min(sortedRows.size(), hitRowIndex + resolvedWindow + 1);
        List<AssetTranscriptRowResponse> contextRows = sortedRows.subList(startIndex, endIndexExclusive);

        return new AssetTranscriptContextResponse(
                assetId,
                transcriptRowId,
                hitRow.segmentIndex(),
                resolvedWindow,
                contextRows
        );
    }

    public List<FastApiTranscriptRowResponse> loadUsableTranscriptRows(Asset asset, ProcessingJob processingJob) {
        if (processingJob.getProcessingJobStatus() != ProcessingJobStatus.SUCCEEDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transcript is not ready until processing reaches terminal success"
            );
        }

        List<FastApiTranscriptRowResponse> transcriptRows = fastApiProcessingClient.getTranscript(processingJob.getFastapiVideoId());

        if (transcriptRows.isEmpty()) {
            assetPersistenceService.updateAssetStatus(asset, AssetStatus.FAILED);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transcript is empty for this asset"
            );
        }

        AssetStatus updatedAssetStatus = asset.getStatus() == AssetStatus.SEARCHABLE
                ? AssetStatus.SEARCHABLE
                : AssetStatus.TRANSCRIPT_READY;
        assetPersistenceService.updateAssetStatus(asset, updatedAssetStatus);

        // TODO: after transcript rows are indexed successfully, move TRANSCRIPT_READY to SEARCHABLE.
        // TODO: if repeated transcript reads become common, consider a local transcript cache or table.
        return transcriptRows;
    }

    public AssetUploadResponse uploadAsset(UUID workspaceId, MultipartFile file, String requestedTitle) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty file is required");
        }

        String originalFilename = resolveOriginalFilename(file);
        String title = resolveTitle(requestedTitle, originalFilename);
        Workspace workspace = workspaceService.resolveWorkspaceOrDefault(workspaceId);

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

        // TODO: replace default-workspace fallback with user-owned workspace selection once auth exists.
        // TODO: decide how to reconcile orphaned upstream tasks if FastAPI accepts upload but DB persistence fails.


        return assetPersistenceService.persistUploadResult(
                originalFilename,
                title,
                initialAssetStatus,
                initialProcessingStatus,
                workspace,
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
        int lastDotIndex = cleanedFilename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < cleanedFilename.length() - 1) {
            String extension = cleanedFilename.substring(lastDotIndex);
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

    private int resolveTranscriptContextWindow(Integer window) {
        if (window == null) {
            return DEFAULT_TRANSCRIPT_CONTEXT_WINDOW;
        }
        if (window <= 0) {
            throw new InvalidTranscriptContextWindowException("window must be greater than 0");
        }
        if (window > MAX_TRANSCRIPT_CONTEXT_WINDOW) {
            throw new InvalidTranscriptContextWindowException(
                    "window must be less than or equal to " + MAX_TRANSCRIPT_CONTEXT_WINDOW
            );
        }
        return window;
    }

    private int findTranscriptRowIndex(List<AssetTranscriptRowResponse> rows, String transcriptRowId) {
        for (int index = 0; index < rows.size(); index++) {
            if (matchesTranscriptRowId(rows.get(index), transcriptRowId)) {
                return index;
            }
        }
        return -1;
    }

    private boolean matchesTranscriptRowId(AssetTranscriptRowResponse row, String transcriptRowId) {
        if (StringUtils.hasText(row.id()) && row.id().equals(transcriptRowId)) {
            return true;
        }
        return row.segmentIndex() != null
                && ("segment-" + row.segmentIndex()).equals(transcriptRowId);
    }

    private List<Asset> loadAssetsForWorkspace(Workspace workspace) {
        List<Asset> assets = new ArrayList<>(assetRepository.findByWorkspace_Id(workspace.getId(), ASSET_LIST_SORT));
        if (!workspace.getId().equals(workspaceService.getDefaultWorkspaceId())) {
            return assets;
        }

        assets.addAll(assetRepository.findByWorkspaceIsNull(ASSET_LIST_SORT));
        assets.sort(Comparator
                .comparing(Asset::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(Asset::getId));
        return assets;
    }

    private Asset backfillWorkspaceIfNeeded(Asset asset, Workspace workspace) {
        if (asset.getWorkspace() != null) {
            return asset;
        }
        return assetPersistenceService.updateAssetWorkspace(asset, workspace);
    }

    private AssetSummaryResponse toAssetSummaryResponse(Asset asset) {
        return new AssetSummaryResponse(
                asset.getId(),
                asset.getTitle(),
                asset.getStatus(),
                asset.getWorkspaceId(),
                asset.getCreatedAt()
        );
    }

    private AssetTranscriptRowResponse toAssetTranscriptRowResponse(FastApiTranscriptRowResponse transcriptRow) {
        return new AssetTranscriptRowResponse(
                transcriptRow.id(),
                transcriptRow.videoId(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt()
        );
    }
}
