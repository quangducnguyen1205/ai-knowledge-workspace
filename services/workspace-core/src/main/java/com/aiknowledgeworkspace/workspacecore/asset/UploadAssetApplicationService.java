package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectKeyFactory;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageProperties;
import com.aiknowledgeworkspace.workspacecore.storage.StoreObjectRequest;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
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
    private final WorkspaceService workspaceService;
    private final ObjectStorageClient objectStorageClient;
    private final ObjectKeyFactory objectKeyFactory;
    private final ObjectStorageProperties objectStorageProperties;

    public UploadAssetApplicationService(
            ProcessingRequestApplication processingRequestApplication,
            DirectProcessingCompatibilityAdapter compatibilityAdapter,
            AssetPersistenceService assetPersistenceService,
            WorkspaceService workspaceService,
            ObjectStorageClient objectStorageClient,
            ObjectKeyFactory objectKeyFactory,
            ObjectStorageProperties objectStorageProperties
    ) {
        this.processingRequestApplication = processingRequestApplication;
        this.compatibilityAdapter = compatibilityAdapter;
        this.assetPersistenceService = assetPersistenceService;
        this.workspaceService = workspaceService;
        this.objectStorageClient = objectStorageClient;
        this.objectKeyFactory = objectKeyFactory;
        this.objectStorageProperties = objectStorageProperties;
    }

    public AssetUploadResponse uploadAsset(UUID workspaceId, MultipartFile file, String requestedTitle) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadRequestException("A non-empty file is required");
        }

        String originalFilename = resolveOriginalFilename(file);
        String title = resolveTitle(requestedTitle, originalFilename);
        Workspace workspace = workspaceService.resolveWorkspaceOrDefault(workspaceId);
        UUID assetId = UUID.randomUUID();
        String objectKey = objectKeyFactory.rawMediaKey(
                workspace.getOwnerId(), workspace.getId(), assetId, originalFilename
        );
        StoredObject storedObject = storeUploadedObject(file, objectKey);

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

    private StoredObject storeUploadedObject(MultipartFile file, String objectKey) {
        try (InputStream inputStream = file.getInputStream()) {
            return objectStorageClient.store(new StoreObjectRequest(
                    objectStorageProperties.getBucket(),
                    objectKey,
                    inputStream,
                    file.getSize(),
                    resolveContentType(file)
            ));
        } catch (IOException exception) {
            throw new InvalidUploadRequestException("Uploaded file could not be read");
        }
    }

    private String resolveContentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
    }

    private void cleanupStoredObjectBestEffort(StoredObject storedObject) {
        try {
            objectStorageClient.delete(storedObject.bucket(), storedObject.objectKey());
        } catch (RuntimeException cleanupException) {
            LOGGER.warn(
                    "Failed to clean up uploaded object {}/{} after upload flow failure",
                    storedObject.bucket(),
                    storedObject.objectKey(),
                    cleanupException
            );
        }
    }

    private String resolveOriginalFilename(MultipartFile file) {
        String cleanedFilename = StringUtils.getFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(cleanedFilename)) {
            return "upload.bin";
        }
        int maxLength = 255;
        if (cleanedFilename.length() <= maxLength) {
            return cleanedFilename;
        }
        int lastDotIndex = cleanedFilename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < cleanedFilename.length() - 1) {
            String extension = cleanedFilename.substring(lastDotIndex);
            int truncatedLength = maxLength - extension.length();
            if (truncatedLength > 0) {
                return cleanedFilename.substring(0, truncatedLength) + extension;
            }
        }
        return cleanedFilename.substring(0, maxLength);
    }

    private String resolveTitle(String requestedTitle, String originalFilename) {
        String title = StringUtils.hasText(requestedTitle) ? requestedTitle.trim() : originalFilename;
        return title.length() > 255 ? title.substring(0, 255) : title;
    }
}
