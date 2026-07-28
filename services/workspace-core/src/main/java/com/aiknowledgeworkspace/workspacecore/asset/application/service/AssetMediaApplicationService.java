package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaNotAvailableException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaReadException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetMediaDescriptor;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetMediaUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectMetadata;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectNotFoundException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReadException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetMediaApplicationService implements AssetMediaUseCase {

    private final AssetQueryApplicationService assetQueries;
    private final ObjectStorageUseCase objectStorage;

    public AssetMediaApplicationService(
            AssetQueryApplicationService assetQueries,
            ObjectStorageUseCase objectStorage
    ) {
        this.assetQueries = assetQueries;
        this.objectStorage = objectStorage;
    }

    @Override
    public AssetMediaDescriptor resolve(UUID assetId) {
        Asset asset = assetQueries.loadAuthorizedAsset(assetId);
        StoredObjectReference reference = uploadReference(asset);
        final StoredObjectMetadata metadata;
        try {
            metadata = objectStorage.stat(reference);
        } catch (StoredObjectNotFoundException exception) {
            throw new AssetMediaNotAvailableException(exception);
        } catch (StoredObjectReadException exception) {
            throw new AssetMediaReadException();
        }

        if (metadata.sizeBytes() <= 0 || metadata.sizeBytes() != asset.getSizeBytes()) {
            throw new AssetMediaNotAvailableException();
        }

        String contentType = StringUtils.hasText(asset.getContentType())
                ? asset.getContentType()
                : metadata.contentType();
        return new AssetMediaDescriptor(
                asset.getId(),
                contentType,
                asset.getOriginalFilename(),
                metadata.sizeBytes(),
                reference
        );
    }

    @Override
    public InputStream openStream(AssetMediaDescriptor descriptor, long offset, long length) {
        if (descriptor == null
                || offset < 0
                || length <= 0
                || offset >= descriptor.totalSizeBytes()
                || length > descriptor.totalSizeBytes() - offset) {
            throw new IllegalArgumentException("Requested media range is outside the resolved object");
        }
        try {
            return objectStorage.openRange(descriptor.storageReference(), offset, length);
        } catch (StoredObjectNotFoundException exception) {
            throw new AssetMediaNotAvailableException(exception);
        } catch (StoredObjectReadException exception) {
            throw new AssetMediaReadException();
        }
    }

    private StoredObjectReference uploadReference(Asset asset) {
        if (asset.getSourceType() != AssetSourceType.UPLOAD
                || !StringUtils.hasText(asset.getOriginalFilename())
                || !StringUtils.hasText(asset.getStorageBucket())
                || !StringUtils.hasText(asset.getObjectKey())
                || asset.getSizeBytes() == null
                || asset.getSizeBytes() <= 0) {
            throw new AssetMediaNotAvailableException();
        }
        return new StoredObjectReference(
                asset.getStorageBucket(),
                asset.getObjectKey(),
                asset.getSizeBytes(),
                asset.getContentType(),
                asset.getEtag()
        );
    }
}
