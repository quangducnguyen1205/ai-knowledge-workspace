package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectKeyFactory;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageProperties;
import com.aiknowledgeworkspace.workspacecore.storage.StoreObjectRequest;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Temporary internal compatibility facade for pre-decomposition unit fixtures.
 * Production adapters and controllers use the dedicated application services directly.
 */
@Deprecated(forRemoval = false)
class AssetService {

    private final UploadAssetApplicationService uploadApplicationService;
    private final AssetQueryApplicationService queryApplicationService;
    private final DirectProcessingCompatibilityAdapter compatibilityAdapter;

    AssetService(
            UploadAssetApplicationService uploadApplicationService,
            AssetQueryApplicationService queryApplicationService,
            DirectProcessingCompatibilityAdapter compatibilityAdapter
    ) {
        this.uploadApplicationService = uploadApplicationService;
        this.queryApplicationService = queryApplicationService;
        this.compatibilityAdapter = compatibilityAdapter;
    }

    AssetService(
            AssetRepository assetRepository,
            ProcessingRequestApplication processingRequestApplication,
            FastApiProcessingClient fastApiProcessingClient,
            AssetPersistenceService assetPersistenceService,
            WorkspaceService workspaceService,
            ObjectStorageClient objectStorageClient,
            ObjectKeyFactory objectKeyFactory,
            ObjectStorageProperties objectStorageProperties
    ) {
        this(legacyWiring(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                objectStorageClient,
                objectKeyFactory,
                objectStorageProperties
        ));
    }

    AssetService(
            AssetRepository assetRepository,
            ProcessingRequestApplication processingRequestApplication,
            FastApiProcessingClient fastApiProcessingClient,
            AssetPersistenceService assetPersistenceService,
            WorkspaceService workspaceService
    ) {
        this(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                new ObjectStorageClient() {
                    @Override
                    public StoredObject store(StoreObjectRequest request) {
                        throw new IllegalStateException("Object storage client is not configured");
                    }

                    @Override
                    public void delete(String bucket, String objectKey) {
                    }
                },
                new ObjectKeyFactory(),
                new ObjectStorageProperties()
        );
    }

    private AssetService(LegacyWiring wiring) {
        this(wiring.uploadApplicationService(), wiring.queryApplicationService(), wiring.compatibilityAdapter());
    }

    Asset getAsset(UUID assetId) {
        return queryApplicationService.getAsset(assetId);
    }

    AssetListResponse listAssets(UUID workspaceId, Integer page, Integer size, AssetStatus status) {
        return queryApplicationService.listAssets(workspaceId, page, size, status);
    }

    AssetStatusResponse getAssetStatus(UUID assetId) {
        return queryApplicationService.getAssetStatus(assetId);
    }

    List<AssetTranscriptRowResponse> getAssetTranscript(UUID assetId) {
        return queryApplicationService.getAssetTranscript(assetId);
    }

    AssetTranscriptContextResponse getAssetTranscriptContext(UUID assetId, String transcriptRowId, Integer window) {
        return queryApplicationService.getAssetTranscriptContext(assetId, transcriptRowId, window);
    }

    List<AssetTranscriptRowSnapshot> loadUsableTranscriptSnapshot(Asset asset, ProcessingJobView processingJob) {
        return compatibilityAdapter.loadOrCaptureTranscript(asset, processingJob);
    }

    AssetUploadResponse uploadAsset(UUID workspaceId, MultipartFile file, String requestedTitle) {
        return uploadApplicationService.uploadAsset(workspaceId, file, requestedTitle);
    }

    private static LegacyWiring legacyWiring(
            AssetRepository assetRepository,
            ProcessingRequestApplication processingRequestApplication,
            FastApiProcessingClient fastApiProcessingClient,
            AssetPersistenceService assetPersistenceService,
            WorkspaceService workspaceService,
            ObjectStorageClient objectStorageClient,
            ObjectKeyFactory objectKeyFactory,
            ObjectStorageProperties objectStorageProperties
    ) {
        AssetTranscriptQueryService transcriptQueryService = new AssetTranscriptQueryService(
                assetRepository, assetPersistenceService, workspaceService
        );
        AssetTranscriptSnapshotService transcriptSnapshotService = new AssetTranscriptSnapshotService(
                assetRepository,
                assetPersistenceService,
                (assetId, rows) -> {
                }
        );
        DirectProcessingCompatibilityAdapter compatibilityAdapter = new DirectProcessingCompatibilityAdapter(
                fastApiProcessingClient, transcriptQueryService, transcriptSnapshotService
        );
        UploadAssetApplicationService uploadApplicationService = new UploadAssetApplicationService(
                processingRequestApplication,
                compatibilityAdapter,
                assetPersistenceService,
                workspaceService,
                objectStorageClient,
                objectKeyFactory,
                objectStorageProperties
        );
        AssetQueryApplicationService queryApplicationService = new AssetQueryApplicationService(
                assetRepository,
                processingRequestApplication,
                compatibilityAdapter,
                assetPersistenceService,
                workspaceService
        );
        return new LegacyWiring(uploadApplicationService, queryApplicationService, compatibilityAdapter);
    }

    private record LegacyWiring(
            UploadAssetApplicationService uploadApplicationService,
            AssetQueryApplicationService queryApplicationService,
            DirectProcessingCompatibilityAdapter compatibilityAdapter
    ) {
    }
}
