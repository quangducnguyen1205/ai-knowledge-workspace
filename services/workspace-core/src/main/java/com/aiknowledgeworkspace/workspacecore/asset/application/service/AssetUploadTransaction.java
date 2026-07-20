package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetUploadResult;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetUploadTransaction {

    private final AssetStore assetStore;
    private final ProcessingRequestUseCase processingRequestUseCase;

    AssetUploadTransaction(AssetStore assetStore, ProcessingRequestUseCase processingRequestUseCase) {
        this.assetStore = assetStore;
        this.processingRequestUseCase = processingRequestUseCase;
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
        ProcessingJobView processingJob = processingRequestUseCase.createKafkaJobAndRequest(
                new ProcessingRequestCommand(
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
