package com.aiknowledgeworkspace.workspacecore.asset.application.query;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetListRequestException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetListResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatusResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetSummaryResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptContextResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.asset.InvalidTranscriptContextWindowException;
import com.aiknowledgeworkspace.workspacecore.asset.ProcessingJobNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.TranscriptRowNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshot;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTaskState;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetQueryApplicationService {

    private static final int DEFAULT_ASSET_LIST_PAGE = 0;
    private static final int DEFAULT_ASSET_LIST_SIZE = 20;
    private static final int MAX_ASSET_LIST_SIZE = 100;
    private static final Sort ASSET_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );
    private static final Comparator<Asset> ASSET_LIST_COMPARATOR = Comparator
            .comparing(Asset::getCreatedAt, Comparator.reverseOrder())
            .thenComparing(Asset::getId, Comparator.reverseOrder());
    private static final int DEFAULT_TRANSCRIPT_CONTEXT_WINDOW = 2;
    private static final int MAX_TRANSCRIPT_CONTEXT_WINDOW = 5;

    private final AssetRepository assetRepository;
    private final ProcessingRequestApplication processingRequestApplication;
    private final DirectProcessingCompatibilityAdapter compatibilityAdapter;
    private final AssetPersistenceService assetPersistenceService;
    private final WorkspaceQueryApplication workspaceQueryApplication;

    public AssetQueryApplicationService(
            AssetRepository assetRepository,
            ProcessingRequestApplication processingRequestApplication,
            DirectProcessingCompatibilityAdapter compatibilityAdapter,
            AssetPersistenceService assetPersistenceService,
            WorkspaceQueryApplication workspaceQueryApplication
    ) {
        this.assetRepository = assetRepository;
        this.processingRequestApplication = processingRequestApplication;
        this.compatibilityAdapter = compatibilityAdapter;
        this.assetPersistenceService = assetPersistenceService;
        this.workspaceQueryApplication = workspaceQueryApplication;
    }

    public Asset getAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
        if (asset.getWorkspace() == null || !workspaceQueryApplication.isOwnedByCurrentUser(asset.getWorkspace())) {
            throw new AssetNotFoundException();
        }
        return asset;
    }

    public AssetListResponse listAssets(UUID workspaceId, Integer page, Integer size, AssetStatus assetStatus) {
        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);
        Workspace workspace = workspaceQueryApplication.resolveWorkspaceOrDefault(workspaceId);
        List<Asset> filteredAssets = loadAssetsForWorkspace(workspace).stream()
                .filter(asset -> assetStatus == null || asset.getStatus() == assetStatus)
                .toList();
        int totalElements = filteredAssets.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / resolvedSize);
        int startIndex = (int) Math.min((long) resolvedPage * resolvedSize, totalElements);
        int endIndex = Math.min(startIndex + resolvedSize, totalElements);
        List<AssetSummaryResponse> items = filteredAssets.subList(startIndex, endIndex).stream()
                .map(this::toSummary)
                .toList();
        return new AssetListResponse(
                items,
                resolvedPage,
                resolvedSize,
                totalElements,
                totalPages,
                resolvedPage + 1 < totalPages
        );
    }

    public AssetStatusResponse getAssetStatus(UUID assetId) {
        Asset asset = getAsset(assetId);
        ProcessingJobView processingJob = processingRequestApplication.findByAssetId(assetId)
                .orElseThrow(ProcessingJobNotFoundException::new);
        if (isTerminal(processingJob.status()) || !StringUtils.hasText(processingJob.fastapiTaskId())) {
            return statusResponse(asset, processingJob);
        }

        DirectProcessingTaskState taskState = compatibilityAdapter.taskState(processingJob.fastapiTaskId());
        return assetPersistenceService.refreshAssetStatus(
                asset,
                processingJob,
                taskState.rawStatus(),
                taskState.processingStatus(),
                taskState.assetStatus()
        );
    }

    public List<AssetTranscriptRowResponse> getAssetTranscript(UUID assetId) {
        Asset asset = getAsset(assetId);
        ProcessingJobView processingJob = processingRequestApplication.findByAssetId(assetId)
                .orElseThrow(ProcessingJobNotFoundException::new);
        return compatibilityAdapter.loadOrCaptureTranscript(asset, processingJob).stream()
                .map(this::toTranscriptResponse)
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
        int endIndex = Math.min(sortedRows.size(), hitRowIndex + resolvedWindow + 1);
        return new AssetTranscriptContextResponse(
                assetId,
                transcriptRowId,
                hitRow.segmentIndex(),
                resolvedWindow,
                new ArrayList<>(sortedRows.subList(startIndex, endIndex))
        );
    }

    private AssetStatusResponse statusResponse(Asset asset, ProcessingJobView processingJob) {
        return new AssetStatusResponse(
                asset.getId(), processingJob.id(), asset.getStatus(), processingJob.status()
        );
    }

    private boolean isTerminal(ProcessingJobStatus status) {
        return status == ProcessingJobStatus.SUCCEEDED || status == ProcessingJobStatus.FAILED;
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
            AssetTranscriptRowResponse row = rows.get(index);
            if ((StringUtils.hasText(row.id()) && row.id().equals(transcriptRowId))
                    || (!StringUtils.hasText(row.id())
                    && row.segmentIndex() != null
                    && ("segment-" + row.segmentIndex()).equals(transcriptRowId))) {
                return index;
            }
        }
        return -1;
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return DEFAULT_ASSET_LIST_PAGE;
        }
        if (page < 0) {
            throw new AssetListRequestException("INVALID_ASSET_PAGE", "page must be greater than or equal to 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_ASSET_LIST_SIZE;
        }
        if (size <= 0) {
            throw new AssetListRequestException("INVALID_ASSET_SIZE", "size must be greater than 0");
        }
        if (size > MAX_ASSET_LIST_SIZE) {
            throw new AssetListRequestException(
                    "INVALID_ASSET_SIZE",
                    "size must be less than or equal to " + MAX_ASSET_LIST_SIZE
            );
        }
        return size;
    }

    private List<Asset> loadAssetsForWorkspace(Workspace workspace) {
        List<Asset> assets = new ArrayList<>(
                assetRepository.findByWorkspace_Id(workspace.getId(), ASSET_LIST_SORT)
        );
        assets.sort(ASSET_LIST_COMPARATOR);
        return assets;
    }

    private AssetSummaryResponse toSummary(Asset asset) {
        return new AssetSummaryResponse(
                asset.getId(), asset.getTitle(), asset.getStatus(), asset.getWorkspaceId(), asset.getCreatedAt()
        );
    }

    private AssetTranscriptRowResponse toTranscriptResponse(AssetTranscriptRowSnapshot row) {
        return new AssetTranscriptRowResponse(
                row.getTranscriptRowId(), row.getVideoId(), row.getSegmentIndex(), row.getText(), row.getCreatedAt()
        );
    }
}
