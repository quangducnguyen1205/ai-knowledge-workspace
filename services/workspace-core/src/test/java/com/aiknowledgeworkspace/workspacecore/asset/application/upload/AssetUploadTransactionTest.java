package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetUploadResult;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetUploadTransactionTest {

    @Mock
    private AssetStore assetStore;

    @Mock
    private ProcessingRequestUseCase processingRequests;

    @Test
    void persistsExplicitUploadShapeBeforeCreatingTheUnchangedV1ProcessingRequest() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        StoredObjectReference storedObject = new StoredObjectReference(
                "workspace-media",
                "users/owner-1/assets/raw/lecture.mp4",
                42L,
                "video/mp4",
                "etag-1"
        );
        when(assetStore.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(processingRequests.createKafkaJobAndRequest(any(ProcessingRequestCommand.class)))
                .thenReturn(new ProcessingJobView(
                        processingJobId, assetId, ProcessingJobStatus.PENDING, "processing_request_pending"
                ));

        AssetUploadResult result = new AssetUploadTransaction(assetStore, processingRequests).persist(
                assetId,
                "lecture.mp4",
                "Lecture",
                workspaceId,
                "owner-1",
                storedObject
        );

        ArgumentCaptor<Asset> savedAsset = ArgumentCaptor.forClass(Asset.class);
        verify(assetStore).save(savedAsset.capture());
        assertThat(savedAsset.getValue().getSourceType()).isEqualTo(AssetSourceType.UPLOAD);
        assertThat(savedAsset.getValue().getYoutubeVideoId()).isNull();
        assertThat(savedAsset.getValue().getStorageBucket()).isEqualTo("workspace-media");
        assertThat(savedAsset.getValue().getObjectKey()).isEqualTo("users/owner-1/assets/raw/lecture.mp4");

        verify(processingRequests).createKafkaJobAndRequest(new ProcessingRequestCommand(
                assetId,
                workspaceId,
                "owner-1",
                "workspace-media",
                "users/owner-1/assets/raw/lecture.mp4",
                "lecture.mp4",
                "video/mp4",
                42L
        ));
        assertThat(result).isEqualTo(new AssetUploadResult(
                assetId,
                processingJobId,
                AssetStatus.PROCESSING,
                workspaceId,
                AssetSourceType.UPLOAD,
                null
        ));
    }
}
