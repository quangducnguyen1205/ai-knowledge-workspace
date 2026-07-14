package com.aiknowledgeworkspace.workspacecore.integration.fastapi.processing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactAccessException;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactValidator;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventApplyException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TranscriptArtifactGatewayAdapterTest {

    private final FastApiProcessingClient client = mock(FastApiProcessingClient.class);
    private final TranscriptArtifactGatewayAdapter gateway = new TranscriptArtifactGatewayAdapter(
            client, new TranscriptArtifactValidator()
    );

    @Test
    void mapsValidatedTransportRowsToProcessingOwnedArtifactRows() {
        UUID processingRequestId = UUID.randomUUID();
        when(client.getTranscriptArtifactRows(processingRequestId.toString())).thenReturn(List.of(
                new FastApiTranscriptRowResponse(
                        "row-1", "video-1", 0, "Validated text", "2026-07-13T00:00:00Z"
                )
        ));

        var rows = gateway.loadValidatedRows(processingRequestId);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo("row-1");
            assertThat(row.videoId()).isEqualTo("video-1");
            assertThat(row.segmentIndex()).isZero();
            assertThat(row.text()).isEqualTo("Validated text");
            assertThat(row.createdAt()).isEqualTo("2026-07-13T00:00:00Z");
        });
    }

    @Test
    void keepsHttpFailureMappingInsideTheGatewayAdapter() {
        UUID processingRequestId = UUID.randomUUID();
        FastApiIntegrationException transportFailure = new FastApiIntegrationException("FastAPI returned HTTP 503");
        when(client.getTranscriptArtifactRows(processingRequestId.toString())).thenThrow(transportFailure);

        assertThatThrownBy(() -> gateway.loadValidatedRows(processingRequestId))
                .isInstanceOf(TranscriptArtifactAccessException.class)
                .hasMessage("FastAPI returned HTTP 503")
                .hasCause(transportFailure);
    }

    @Test
    void keepsRequiredArtifactFieldValidationInTheProcessingBoundary() {
        UUID processingRequestId = UUID.randomUUID();
        when(client.getTranscriptArtifactRows(processingRequestId.toString())).thenReturn(List.of(
                new FastApiTranscriptRowResponse(
                        "row-1", "video-1", null, "Validated text", "2026-07-13T00:00:00Z"
                )
        ));

        assertThatThrownBy(() -> gateway.loadValidatedRows(processingRequestId))
                .isInstanceOf(ProcessingResultEventApplyException.class)
                .hasMessage("Transcript artifact row segmentIndex is required");
    }
}
