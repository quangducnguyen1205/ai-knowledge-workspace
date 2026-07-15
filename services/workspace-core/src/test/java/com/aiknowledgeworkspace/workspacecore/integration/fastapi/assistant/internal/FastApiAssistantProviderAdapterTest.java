package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderRequest;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderResponse;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FastApiAssistantProviderAdapterTest {

    @Test
    void mapsAssistantOwnedContractToInternalWireContractAndBack() {
        FastApiAssistantClient client = mock(FastApiAssistantClient.class);
        when(client.answer(any())).thenReturn(new FastApiAssistantAnswerResponse(
                "A bounded answer",
                List.of("source-1"),
                false
        ));
        FastApiAssistantProviderAdapter adapter = new FastApiAssistantProviderAdapter(client);
        UUID assetId = UUID.randomUUID();

        AssistantProviderResponse response = adapter.answer(new AssistantProviderRequest(
                "What is the topic?",
                List.of(new AssistantProviderSource(
                        "source-1",
                        assetId,
                        "Lecture",
                        "row-1",
                        3,
                        "2026-07-15T00:00:00Z",
                        "bounded source text"
                ))
        ));

        ArgumentCaptor<FastApiAssistantAnswerRequest> requestCaptor =
                ArgumentCaptor.forClass(FastApiAssistantAnswerRequest.class);
        verify(client).answer(requestCaptor.capture());
        FastApiAssistantAnswerRequest wireRequest = requestCaptor.getValue();
        assertThat(wireRequest.question()).isEqualTo("What is the topic?");
        assertThat(wireRequest.sources()).singleElement().satisfies(source -> {
            assertThat(source.sourceId()).isEqualTo("source-1");
            assertThat(source.assetId()).isEqualTo(assetId);
            assertThat(source.assetTitle()).isEqualTo("Lecture");
            assertThat(source.transcriptRowId()).isEqualTo("row-1");
            assertThat(source.segmentIndex()).isEqualTo(3);
            assertThat(source.createdAt()).isEqualTo("2026-07-15T00:00:00Z");
            assertThat(source.text()).isEqualTo("bounded source text");
        });
        assertThat(response.answer()).isEqualTo("A bounded answer");
        assertThat(response.citedSourceIds()).containsExactly("source-1");
        assertThat(response.insufficientContext()).isFalse();
    }
}
