package com.aiknowledgeworkspace.workspacecore.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContextPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptSegment;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantAnswerRequest;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantAnswerResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantClient;
import com.aiknowledgeworkspace.workspacecore.search.SearchResponse;
import com.aiknowledgeworkspace.workspacecore.search.SearchResultResponse;
import com.aiknowledgeworkspace.workspacecore.search.SearchService;
import java.util.List;
import java.util.Optional;
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
    private SearchService searchService;

    @Mock
    private AssetTranscriptQueryService assetReadService;

    @Mock
    private FastApiAssistantClient fastApiAssistantClient;

    private AssistantAnswerService assistantAnswerService;

    @BeforeEach
    void setUp() {
        assistantAnswerService = new AssistantAnswerService(
                new AssistantContextService(searchPort(), transcriptPort()),
                fastApiAssistantClient
        );
    }

    private AssistantSearchPort searchPort() {
        return (query, workspaceId, assetId) -> {
            SearchResponse response = searchService.search(query, workspaceId, assetId);
            return new AssistantSearchPage(response.workspaceIdFilter(), response.results().stream().map(result ->
                    new AssistantSearchHit(result.assetId(), result.assetTitle(), result.transcriptRowId(),
                            result.segmentIndex(), result.text(), result.createdAt(), result.score())
            ).toList());
        };
    }

    private AssistantTranscriptContextPort transcriptPort() {
        return (assetId, workspaceId, rowId, window) -> assetReadService.findSearchableTranscriptContext(
                assetId, workspaceId, rowId, window
        ).map(context -> new AssistantTranscriptContext(
                context.assetId(), context.assetTitle(), context.transcriptRowId(), context.hitSegmentIndex(),
                context.window(), context.rows().stream().map(row -> new AssistantTranscriptSegment(
                        row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                )).toList()
        ));
    }

    @Test
    void answerBuildsInternalRequestFromBoundedAuthorizedSearchableContext() {
        UUID workspaceId = UUID.randomUUID();
        UUID approvedAssetId = UUID.randomUUID();
        UUID staleAssetId = UUID.randomUUID();
        when(searchService.search("dynamic programming", workspaceId, null))
                .thenReturn(new SearchResponse(
                        "dynamic programming",
                        workspaceId,
                        null,
                        2,
                        List.of(
                                result(approvedAssetId, "row-1", 1),
                                result(staleAssetId, "row-2", 2)
                        )
                ));
        when(assetReadService.findSearchableTranscriptContext(approvedAssetId, workspaceId, "row-1", 1))
                .thenReturn(Optional.of(context(
                        approvedAssetId,
                        "Lecture 1",
                        "row-1",
                        1,
                        1,
                        row("row-1", 1, "approved transcript context")
                )));
        when(assetReadService.findSearchableTranscriptContext(staleAssetId, workspaceId, "row-2", 1))
                .thenReturn(Optional.empty());
        when(fastApiAssistantClient.answer(any())).thenAnswer(invocation -> {
            FastApiAssistantAnswerRequest internalRequest = invocation.getArgument(0);
            return new FastApiAssistantAnswerResponse(
                    "Use the approved context.",
                    List.of(internalRequest.sources().get(0).sourceId()),
                    false
            );
        });

        AssistantAnswerResponse response = assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "  dynamic programming  ",
                null,
                2,
                1
        ));

        ArgumentCaptor<FastApiAssistantAnswerRequest> requestCaptor =
                ArgumentCaptor.forClass(FastApiAssistantAnswerRequest.class);
        verify(fastApiAssistantClient).answer(requestCaptor.capture());
        FastApiAssistantAnswerRequest internalRequest = requestCaptor.getValue();
        assertThat(internalRequest.question()).isEqualTo("dynamic programming");
        assertThat(internalRequest.sources()).hasSize(1);
        assertThat(internalRequest.sources().get(0).assetId()).isEqualTo(approvedAssetId);
        assertThat(internalRequest.sources().get(0).assetTitle()).isEqualTo("Lecture 1");
        assertThat(internalRequest.sources().get(0).transcriptRowId()).isEqualTo("row-1");
        assertThat(internalRequest.sources().get(0).text()).isEqualTo("approved transcript context");
        assertThat(internalRequest.sources().get(0).sourceId()).startsWith("src-");

        assertThat(response.answer()).isEqualTo("Use the approved context.");
        assertThat(response.insufficientContext()).isFalse();
        assertThat(response.citations()).hasSize(1);
        AssistantAnswerCitationResponse citation = response.citations().get(0);
        assertThat(citation.sourceId()).isEqualTo(internalRequest.sources().get(0).sourceId());
        assertThat(citation.assetId()).isEqualTo(approvedAssetId);
        assertThat(citation.assetTitle()).isEqualTo("Lecture 1");
        assertThat(citation.transcriptRowId()).isEqualTo("row-1");
        verify(assetReadService).findSearchableTranscriptContext(approvedAssetId, workspaceId, "row-1", 1);
        verify(assetReadService).findSearchableTranscriptContext(staleAssetId, workspaceId, "row-2", 1);
    }

    @Test
    void insufficientContextDoesNotFabricateCitations() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        oneApprovedSource(workspaceId, assetId);
        when(fastApiAssistantClient.answer(any())).thenReturn(new FastApiAssistantAnswerResponse(
                "I do not have enough context to answer.",
                List.of(),
                true
        ));

        AssistantAnswerResponse response = assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "unknown detail",
                null,
                null,
                null
        ));

        assertThat(response.insufficientContext()).isTrue();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).isEqualTo("I do not have enough context to answer.");
    }

    @Test
    void unknownCitedSourceIdFailsClosed() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        oneApprovedSource(workspaceId, assetId);
        when(fastApiAssistantClient.answer(any())).thenReturn(new FastApiAssistantAnswerResponse(
                "A grounded answer.",
                List.of("src-unknown"),
                false
        ));

        assertThatThrownBy(() -> assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "grounded question",
                null,
                null,
                null
        ))).isInstanceOf(AssistantProviderUnavailableException.class)
                .hasMessage("Assistant provider is unavailable");
    }

    @Test
    void nonInsufficientAnswerMustCiteAtLeastOneSuppliedSource() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        oneApprovedSource(workspaceId, assetId);
        when(fastApiAssistantClient.answer(any())).thenReturn(new FastApiAssistantAnswerResponse(
                "A citation-free answer is not accepted.",
                List.of(),
                false
        ));

        assertThatThrownBy(() -> assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "grounded question",
                null,
                null,
                null
        ))).isInstanceOf(AssistantProviderUnavailableException.class);
    }

    @Test
    void providerFailureBecomesAssistantProviderUnavailable() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        oneApprovedSource(workspaceId, assetId);
        when(fastApiAssistantClient.answer(any()))
                .thenThrow(new RuntimeException("FastAPI returned HTTP 503 while trying to generate"));

        assertThatThrownBy(() -> assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "grounded question",
                null,
                null,
                null
        ))).isInstanceOf(AssistantProviderUnavailableException.class)
                .hasMessage("Assistant provider is unavailable");
    }

    @Test
    void invalidQuestionIsRejectedBeforeRetrievalOrProviderCall() {
        UUID workspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "   ",
                null,
                null,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("question is required");
        assertThatThrownBy(() -> assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "a".repeat(AssistantContextService.MAX_QUERY_LENGTH + 1),
                null,
                null,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("question must be at most 500 characters");

        verifyNoInteractions(searchService, assetReadService, fastApiAssistantClient);
    }

    @Test
    void providerIsNotCalledWhenContextValidationFails() {
        UUID workspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> assistantAnswerService.answer(new AssistantAnswerRequest(
                workspaceId,
                "question",
                null,
                0,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class);
        verifyNoInteractions(searchService, assetReadService);
        verify(fastApiAssistantClient, never()).answer(any());
    }

    private void oneApprovedSource(UUID workspaceId, UUID assetId) {
        when(searchService.search(any(), any(), any()))
                .thenReturn(new SearchResponse(
                        "query",
                        workspaceId,
                        null,
                        1,
                        List.of(result(assetId, "row-1", 1))
                ));
        when(assetReadService.findSearchableTranscriptContext(assetId, workspaceId, "row-1", 1))
                .thenReturn(Optional.of(context(
                        assetId,
                        "Lecture",
                        "row-1",
                        1,
                        1,
                        row("row-1", 1, "source text")
                )));
    }

    private SearchResultResponse result(UUID assetId, String transcriptRowId, Integer segmentIndex) {
        return new SearchResultResponse(
                assetId,
                "Indexed Lecture",
                transcriptRowId,
                segmentIndex,
                "indexed text",
                "2026-06-25T00:00:00Z",
                2.5
        );
    }

    private AssetTranscriptContext context(
            UUID assetId,
            String assetTitle,
            String transcriptRowId,
            Integer hitSegmentIndex,
            int window,
            AssetTranscriptRowView... rows
    ) {
        return new AssetTranscriptContext(
                assetId,
                assetTitle,
                transcriptRowId,
                hitSegmentIndex,
                window,
                List.of(rows)
        );
    }

    private AssetTranscriptRowView row(String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowView(
                id,
                "video-1",
                segmentIndex,
                text,
                "2026-06-25T00:00:0" + segmentIndex + "Z"
        );
    }
}
