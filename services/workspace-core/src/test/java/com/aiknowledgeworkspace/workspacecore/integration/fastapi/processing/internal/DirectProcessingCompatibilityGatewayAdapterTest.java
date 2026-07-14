package com.aiknowledgeworkspace.workspacecore.integration.fastapi.processing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadCommand;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class DirectProcessingCompatibilityGatewayAdapterTest {

    private final FastApiProcessingClient client = mock(FastApiProcessingClient.class);
    private final DirectProcessingCompatibilityGatewayAdapter adapter =
            new DirectProcessingCompatibilityGatewayAdapter(client);

    @Test
    void mapsUploadContractToAssetOwnedResult() {
        when(client.uploadVideo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("video.mp4"),
                org.mockito.ArgumentMatchers.eq("Lecture")))
                .thenReturn(new FastApiUploadResponse("task-1", "pending", "video-1"));

        var result = adapter.upload(new DirectProcessingUploadCommand(
                new ByteArrayResource(new byte[]{1}), "video.mp4", "Lecture"
        ));

        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.videoId()).isEqualTo("video-1");
        assertThat(result.processingStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(result.assetStatus()).isEqualTo(AssetStatus.PROCESSING);
    }

    @Test
    void rejectsMalformedUploadContractInsideIntegrationBoundary() {
        when(client.uploadVideo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new FastApiUploadResponse("", "pending", "video-1"));

        assertThatThrownBy(() -> adapter.upload(new DirectProcessingUploadCommand(
                new ByteArrayResource(new byte[]{1}), "video.mp4", "Lecture"
        )))
                .isInstanceOf(DirectProcessingIntegrationException.class)
                .hasMessage("FastAPI upload response did not include task_id");
    }

    @Test
    void mapsTranscriptRowsWithoutExposingTransportTypes() {
        when(client.getTranscript("video-1")).thenReturn(java.util.List.of(
                new FastApiTranscriptRowResponse("row-1", "video-1", 0, "text", "2026-07-13T00:00:00Z")
        ));

        var rows = adapter.transcriptRows("video-1");

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo("row-1");
            assertThat(row.segmentIndex()).isZero();
            assertThat(row.text()).isEqualTo("text");
        });
    }
}
