package com.aiknowledgeworkspace.workspacecore.asset.application.upload;

import com.aiknowledgeworkspace.workspacecore.asset.AssetUploadResponse;
import com.aiknowledgeworkspace.workspacecore.asset.InvalidUploadRequestException;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;
import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadAssetApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadAssetApplicationService.class);

    private final ProcessingRequestApplication processingRequestApplication;
    private final DirectProcessingCompatibilityAdapter compatibilityAdapter;
    private final AssetPersistenceService assetPersistenceService;
    private final WorkspaceQueryApplication workspaceQueryApplication;
    private final ObjectStorageApplication objectStorageApplication;
    private final SupportedUploadMediaPolicy supportedUploadMediaPolicy;

    public UploadAssetApplicationService(
            ProcessingRequestApplication processingRequestApplication,
            DirectProcessingCompatibilityAdapter compatibilityAdapter,
            AssetPersistenceService assetPersistenceService,
            WorkspaceQueryApplication workspaceQueryApplication,
            ObjectStorageApplication objectStorageApplication,
            SupportedUploadMediaPolicy supportedUploadMediaPolicy
    ) {
        this.processingRequestApplication = processingRequestApplication;
        this.compatibilityAdapter = compatibilityAdapter;
        this.assetPersistenceService = assetPersistenceService;
        this.workspaceQueryApplication = workspaceQueryApplication;
        this.objectStorageApplication = objectStorageApplication;
        this.supportedUploadMediaPolicy = supportedUploadMediaPolicy;
    }

    public AssetUploadResponse uploadAsset(UUID workspaceId, MultipartFile file, String requestedTitle) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadRequestException("A non-empty file is required");
        }

        Workspace workspace = workspaceQueryApplication.resolveWorkspaceOrDefault(workspaceId);
        ValidatedUploadMedia uploadMedia = supportedUploadMediaPolicy.validate(file);
        String originalFilename = uploadMedia.originalFilename();
        String title = resolveTitle(requestedTitle, originalFilename);
        UUID assetId = UUID.randomUUID();
        StoredObjectReference storedObject = storeUploadedObject(
                file, workspace.getOwnerId(), workspace.getId(), assetId, uploadMedia
        );

        try {
            if (!processingRequestApplication.usesKafkaRequestMode()) {
                DirectProcessingUploadResult result = compatibilityAdapter.upload(
                        file.getResource(), originalFilename, title
                );
                return assetPersistenceService.persistDirectUploadResult(
                        assetId, originalFilename, title, workspace, storedObject, result
                );
            }
            return assetPersistenceService.persistKafkaRequestUpload(
                    assetId, originalFilename, title, workspace, storedObject
            );
        } catch (RuntimeException exception) {
            cleanupStoredObjectBestEffort(storedObject);
            throw exception;
        }
    }

    private StoredObjectReference storeUploadedObject(
            MultipartFile file, String userId, UUID workspaceId, UUID assetId, ValidatedUploadMedia uploadMedia
    ) {
        try (InputStream inputStream = file.getInputStream()) {
            return objectStorageApplication.store(new StoreObjectCommand(
                    userId,
                    workspaceId,
                    assetId,
                    uploadMedia.originalFilename(),
                    inputStream,
                    file.getSize(),
                    uploadMedia.contentType()
            ));
        } catch (IOException exception) {
            throw new InvalidUploadRequestException("Uploaded file could not be read");
        }
    }

    private void cleanupStoredObjectBestEffort(StoredObjectReference storedObject) {
        try {
            objectStorageApplication.delete(storedObject);
        } catch (RuntimeException cleanupException) {
            LOGGER.warn(
                    "Failed to clean up uploaded object {}/{} after upload flow failure",
                    storedObject.bucket(),
                    storedObject.objectKey(),
                    cleanupException
            );
        }
    }

    private String resolveTitle(String requestedTitle, String originalFilename) {
        String title = StringUtils.hasText(requestedTitle) ? requestedTitle.trim() : originalFilename;
        return title.length() > 255 ? title.substring(0, 255) : title;
    }
}
