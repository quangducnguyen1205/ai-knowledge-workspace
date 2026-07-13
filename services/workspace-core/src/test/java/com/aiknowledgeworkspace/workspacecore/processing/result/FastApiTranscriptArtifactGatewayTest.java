package com.aiknowledgeworkspace.workspacecore.processing.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FastApiTranscriptArtifactGatewayTest {

    private final FastApiProcessingClient client = mock(FastApiProcessingClient.class);
    private final FastApiTranscriptArtifactGateway gateway = new FastApiTranscriptArtifactGateway(
            client,
            new TranscriptArtifactValidator()
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
}
