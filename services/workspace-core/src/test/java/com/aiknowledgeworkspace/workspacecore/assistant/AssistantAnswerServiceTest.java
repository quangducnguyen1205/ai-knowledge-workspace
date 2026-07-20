package com.aiknowledgeworkspace.workspacecore.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.assistant.application.AssistantAnswerService;
import com.aiknowledgeworkspace.workspacecore.assistant.application.AssistantContextService;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerCommand;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantCitation;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextSource;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantAnswerProviderPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderRequest;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantAnswerServiceTest {

    @Mock
    private AssistantContextService contextService;

    @Mock
    private AssistantAnswerProviderPort provider;

    private AssistantAnswerService service;

    @BeforeEach
    void setUp() {
        service = new AssistantAnswerService(contextService, provider);
    }

    @Test
    void answerUsesOnlyApplicationContextAndMapsApprovedCitation() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(contextService.query(any())).thenReturn(context(workspaceId, assetId));
        when(provider.answer(any())).thenAnswer(invocation -> {
            AssistantProviderRequest request = invocation.getArgument(0);
            return new AssistantProviderResponse(
                    "Grounded answer",
                    List.of(request.sources().get(0).sourceId()),
                    false
            );
        });

        AssistantAnswerResult result = service.answer(new AssistantAnswerCommand(
                workspaceId, "  dynamic programming  ", null, 2, 1
        ));

        assertThat(result.answer()).isEqualTo("Grounded answer");
        assertThat(result.insufficientContext()).isFalse();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).assetId()).isEqualTo(assetId);
        ArgumentCaptor<AssistantProviderRequest> request = ArgumentCaptor.forClass(AssistantProviderRequest.class);
        verify(provider).answer(request.capture());
        assertThat(request.getValue().question()).isEqualTo("dynamic programming");
        assertThat(request.getValue().sources().get(0).text()).isEqualTo("approved context");
    }

    @Test
    void unknownProviderCitationFailsClosed() {
        UUID workspaceId = UUID.randomUUID();
        when(contextService.query(any())).thenReturn(context(workspaceId, UUID.randomUUID()));
        when(provider.answer(any())).thenReturn(new AssistantProviderResponse(
                "Answer", List.of("src-not-supplied"), false
        ));

        assertThatThrownBy(() -> service.answer(command(workspaceId)))
                .isInstanceOf(AssistantProviderUnavailableException.class)
                .hasMessage("Assistant provider is unavailable");
    }

    @Test
    void nonInsufficientAnswerRequiresACitation() {
        UUID workspaceId = UUID.randomUUID();
        when(contextService.query(any())).thenReturn(context(workspaceId, UUID.randomUUID()));
        when(provider.answer(any())).thenReturn(new AssistantProviderResponse("Answer", List.of(), false));

        assertThatThrownBy(() -> service.answer(command(workspaceId)))
                .isInstanceOf(AssistantProviderUnavailableException.class);
    }

    @Test
    void insufficientContextDoesNotFabricateCitations() {
        UUID workspaceId = UUID.randomUUID();
        when(contextService.query(any())).thenReturn(context(workspaceId, UUID.randomUUID()));
        when(provider.answer(any())).thenReturn(new AssistantProviderResponse(
                "Insufficient context", List.of(), true
        ));

        AssistantAnswerResult result = service.answer(command(workspaceId));

        assertThat(result.insufficientContext()).isTrue();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void providerFailureIsTranslatedAtTheOutboundBoundary() {
        UUID workspaceId = UUID.randomUUID();
        when(contextService.query(any())).thenReturn(context(workspaceId, UUID.randomUUID()));
        when(provider.answer(any())).thenThrow(new RuntimeException("FastAPI HTTP 503 secret"));

        assertThatThrownBy(() -> service.answer(command(workspaceId)))
                .isInstanceOf(AssistantProviderUnavailableException.class)
                .hasMessage("Assistant provider is unavailable");
    }

    @Test
    void invalidQuestionStopsBeforeContextOrProviderCalls() {
        assertThatThrownBy(() -> service.answer(new AssistantAnswerCommand(
                UUID.randomUUID(), "   ", null, null, null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("question is required");

        verifyNoInteractions(contextService, provider);
        verify(provider, never()).answer(any());
    }

    private AssistantAnswerCommand command(UUID workspaceId) {
        return new AssistantAnswerCommand(workspaceId, "question", null, null, null);
    }

    private AssistantContextResult context(UUID workspaceId, UUID assetId) {
        AssistantContextSource source = new AssistantContextSource(
                assetId,
                "Lecture",
                "row-1",
                1,
                "2026-01-01T00:00:00Z",
                "approved context",
                new AssistantCitation(assetId, "row-1", 1)
        );
        return new AssistantContextResult(workspaceId, "dynamic programming", List.of(source));
    }
}
