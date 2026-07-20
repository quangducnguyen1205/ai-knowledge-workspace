package com.aiknowledgeworkspace.workspacecore.asset.application.upload;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.KafkaProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetUploadTransaction {

    private final AssetStore assetStore;
    private final ProcessingRequestApplication processingRequestApplication;

    AssetUploadTransaction(AssetStore assetStore, ProcessingRequestApplication processingRequestApplication) {
        this.assetStore = assetStore;
        this.processingRequestApplication = processingRequestApplication;
    }

    @Transactional
    AssetUploadResult persist(
            UUID assetId,
            String originalFilename,
            String title,
            UUID workspaceId,
            String ownerId,
            StoredObjectReference storedObject
    ) {
        Asset asset = assetStore.save(new Asset(
                assetId,
                originalFilename,
                title,
                AssetStatus.PROCESSING,
                workspaceId,
                storedObject.bucket(),
                storedObject.objectKey(),
                storedObject.contentType(),
                storedObject.sizeBytes(),
                storedObject.eTag()
        ));
        ProcessingJobView processingJob = processingRequestApplication.createKafkaJobAndRequest(
                new KafkaProcessingRequestCommand(
                        asset.getId(),
                        workspaceId,
                        ownerId,
                        storedObject.bucket(),
                        storedObject.objectKey(),
                        asset.getOriginalFilename(),
                        storedObject.contentType(),
                        storedObject.sizeBytes()
                )
        );
        return new AssetUploadResult(asset.getId(), processingJob.id(), asset.getStatus(), asset.getWorkspaceId());
    }
}
