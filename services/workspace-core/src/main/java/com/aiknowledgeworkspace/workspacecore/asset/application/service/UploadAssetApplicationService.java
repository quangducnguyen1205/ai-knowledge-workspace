package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.AssetUploadCommand;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetUploadResult;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidUploadRequestException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetUploadUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UploadAssetApplicationService implements AssetUploadUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadAssetApplicationService.class);

    private final AssetUploadTransaction assetUploadTransaction;
    private final WorkspaceAccessUseCase workspaceQueryApplication;
    private final ObjectStorageUseCase objectStorageApplication;
    private final SupportedUploadMediaPolicy supportedUploadMediaPolicy;

    public UploadAssetApplicationService(
            AssetUploadTransaction assetUploadTransaction,
            WorkspaceAccessUseCase workspaceQueryApplication,
            ObjectStorageUseCase objectStorageApplication,
            SupportedUploadMediaPolicy supportedUploadMediaPolicy
    ) {
        this.assetUploadTransaction = assetUploadTransaction;
        this.workspaceQueryApplication = workspaceQueryApplication;
        this.objectStorageApplication = objectStorageApplication;
        this.supportedUploadMediaPolicy = supportedUploadMediaPolicy;
    }

    @Override
    public AssetUploadResult upload(AssetUploadCommand command) {
        if (command == null || command.content() == null || command.sizeBytes() <= 0) {
            throw new InvalidUploadRequestException("A non-empty file is required");
        }

        WorkspaceAccess workspace = workspaceQueryApplication.resolveWorkspaceOrDefault(command.workspaceId());
        ValidatedUploadMedia uploadMedia = supportedUploadMediaPolicy.validate(command);
        String originalFilename = uploadMedia.originalFilename();
        String title = resolveTitle(command.requestedTitle(), originalFilename);
        UUID assetId = UUID.randomUUID();
        StoredObjectReference storedObject = storeUploadedObject(
                command, workspace.ownerId(), workspace.workspaceId(), assetId, uploadMedia
        );

        try {
            return assetUploadTransaction.persist(
                    assetId, originalFilename, title, workspace.workspaceId(), workspace.ownerId(), storedObject
            );
        } catch (RuntimeException exception) {
            cleanupStoredObjectBestEffort(storedObject);
            throw exception;
        }
    }

    private StoredObjectReference storeUploadedObject(
            AssetUploadCommand command,
            String userId,
            UUID workspaceId,
            UUID assetId,
            ValidatedUploadMedia uploadMedia
    ) {
        try (InputStream inputStream = command.content().openStream()) {
            return objectStorageApplication.store(new StoreObjectCommand(
                    userId,
                    workspaceId,
                    assetId,
                    uploadMedia.originalFilename(),
                    inputStream,
                    command.sizeBytes(),
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
