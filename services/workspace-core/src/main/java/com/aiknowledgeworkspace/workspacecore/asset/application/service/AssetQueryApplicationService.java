package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPage;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetSummary;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetStatusView;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetListRequestException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidTranscriptContextWindowException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.ProcessingJobNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptRowNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptUnavailableException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetQueryApplicationService implements AssetQueryUseCase {

    private static final int DEFAULT_ASSET_LIST_PAGE = 0;
    private static final int DEFAULT_ASSET_LIST_SIZE = 20;
    private static final int MAX_ASSET_LIST_SIZE = 100;
    private static final Comparator<Asset> ASSET_LIST_COMPARATOR = Comparator
            .comparing(Asset::getCreatedAt, Comparator.reverseOrder())
            .thenComparing(Asset::getId, Comparator.reverseOrder());
    private static final int DEFAULT_TRANSCRIPT_CONTEXT_WINDOW = 2;
    private static final int MAX_TRANSCRIPT_CONTEXT_WINDOW = 5;

    private final AssetStore assetStore;
    private final ProcessingRequestUseCase processingRequestUseCase;
    private final AssetTranscriptQueryService transcriptQueryService;
    private final WorkspaceAccessUseCase workspaceQueryApplication;

    public AssetQueryApplicationService(
            AssetStore assetStore,
            ProcessingRequestUseCase processingRequestUseCase,
            AssetTranscriptQueryService transcriptQueryService,
            WorkspaceAccessUseCase workspaceQueryApplication
    ) {
        this.assetStore = assetStore;
        this.processingRequestUseCase = processingRequestUseCase;
        this.transcriptQueryService = transcriptQueryService;
        this.workspaceQueryApplication = workspaceQueryApplication;
    }

    @Override
    public AssetView getAsset(UUID assetId) {
        return AssetView.from(loadAuthorizedAsset(assetId));
    }

    @Override
    public AssetPage listAssets(UUID workspaceId, Integer page, Integer size, AssetStatus assetStatus) {
        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);
        WorkspaceAccess workspace = workspaceQueryApplication.resolveWorkspaceOrDefault(workspaceId);
        List<Asset> filteredAssets = loadAssetsForWorkspace(workspace).stream()
                .filter(asset -> assetStatus == null || asset.getStatus() == assetStatus)
                .toList();
        int totalElements = filteredAssets.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / resolvedSize);
        int startIndex = (int) Math.min((long) resolvedPage * resolvedSize, totalElements);
        int endIndex = Math.min(startIndex + resolvedSize, totalElements);
        List<AssetSummary> items = filteredAssets.subList(startIndex, endIndex).stream()
                .map(this::toSummary)
                .toList();
        return new AssetPage(
                items,
                resolvedPage,
                resolvedSize,
                totalElements,
                totalPages,
                resolvedPage + 1 < totalPages
        );
    }

    @Override
    public AssetStatusView getAssetStatus(UUID assetId) {
        Asset asset = loadAuthorizedAsset(assetId);
        ProcessingJobView processingJob = processingRequestUseCase.findByAssetId(assetId)
                .orElseThrow(ProcessingJobNotFoundException::new);
        return statusView(asset, processingJob);
    }

    @Override
    public List<AssetTranscriptRowView> getAssetTranscript(UUID assetId) {
        loadAuthorizedAsset(assetId);
        ProcessingJobView processingJob = processingRequestUseCase.findByAssetId(assetId)
                .orElseThrow(ProcessingJobNotFoundException::new);
        if (processingJob.status() != ProcessingJobStatus.SUCCEEDED) {
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_READY",
                    "Transcript is not ready until processing reaches terminal success"
            );
        }
        List<AssetTranscriptRowView> rows = transcriptQueryService.loadUsableSnapshot(assetId);
        if (rows.isEmpty()) {
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_AVAILABLE",
                    "Canonical transcript is unavailable for this asset"
            );
        }
        return rows;
    }

    @Override
    public AssetTranscriptContext getAssetTranscriptContext(
            UUID assetId,
            String transcriptRowId,
            Integer window
    ) {
        int resolvedWindow = resolveTranscriptContextWindow(window);
        Asset asset = loadAuthorizedAsset(assetId);
        List<AssetTranscriptRowView> sortedRows = new ArrayList<>(getAssetTranscript(assetId));
        sortedRows.sort(Comparator.comparing(
                AssetTranscriptRowView::segmentIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));
        int hitRowIndex = findTranscriptRowIndex(sortedRows, transcriptRowId);
        if (hitRowIndex < 0) {
            throw new TranscriptRowNotFoundException(assetId, transcriptRowId);
        }
        AssetTranscriptRowView hitRow = sortedRows.get(hitRowIndex);
        int startIndex = Math.max(0, hitRowIndex - resolvedWindow);
        int endIndex = Math.min(sortedRows.size(), hitRowIndex + resolvedWindow + 1);
        return new AssetTranscriptContext(
                assetId,
                asset.getTitle(),
                transcriptRowId,
                hitRow.segmentIndex(),
                resolvedWindow,
                new ArrayList<>(sortedRows.subList(startIndex, endIndex))
        );
    }

    public Asset loadAuthorizedAsset(UUID assetId) {
        Asset asset = assetStore.findById(assetId).orElseThrow(AssetNotFoundException::new);
        if (!workspaceQueryApplication.isOwnedByCurrentUser(asset.getWorkspaceId())) {
            throw new AssetNotFoundException();
        }
        return asset;
    }

    private AssetStatusView statusView(Asset asset, ProcessingJobView processingJob) {
        return new AssetStatusView(
                asset.getId(), processingJob.id(), asset.getStatus(), processingJob.status()
        );
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

    private int findTranscriptRowIndex(List<AssetTranscriptRowView> rows, String transcriptRowId) {
        for (int index = 0; index < rows.size(); index++) {
            AssetTranscriptRowView row = rows.get(index);
            if ((org.springframework.util.StringUtils.hasText(row.id()) && row.id().equals(transcriptRowId))
                    || (!org.springframework.util.StringUtils.hasText(row.id())
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

    private List<Asset> loadAssetsForWorkspace(WorkspaceAccess workspace) {
        List<Asset> assets = new ArrayList<>(
                assetStore.findByWorkspaceId(workspace.workspaceId())
        );
        assets.sort(ASSET_LIST_COMPARATOR);
        return assets;
    }

    private AssetSummary toSummary(Asset asset) {
        return new AssetSummary(
                asset.getId(), asset.getTitle(), asset.getStatus(), asset.getWorkspaceId(), asset.getCreatedAt()
        );
    }

}
