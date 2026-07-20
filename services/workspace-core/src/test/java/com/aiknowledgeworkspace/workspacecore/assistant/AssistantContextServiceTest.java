package com.aiknowledgeworkspace.workspacecore.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.assistant.application.AssistantContextService;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContextPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptSegment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantContextServiceTest {

    @Mock
    private AssistantSearchPort search;

    @Mock
    private AssistantTranscriptContextPort transcripts;

    private AssistantContextService service;

    @BeforeEach
    void setUp() {
        service = new AssistantContextService(search, transcripts);
    }

    @Test
    void queryReauthorizesHitsThroughSearchableCanonicalContext() {
        UUID workspaceId = UUID.randomUUID();
        UUID approvedAsset = UUID.randomUUID();
        UUID staleAsset = UUID.randomUUID();
        when(search.search("query", workspaceId, null)).thenReturn(new AssistantSearchPage(
                workspaceId,
                List.of(hit(approvedAsset, "row-1", 1), hit(staleAsset, "row-2", 2))
        ));
        when(transcripts.findSearchableTranscriptContext(approvedAsset, workspaceId, "row-1", 1))
                .thenReturn(Optional.of(context(approvedAsset, "row-1", 1, "canonical context")));
        when(transcripts.findSearchableTranscriptContext(staleAsset, workspaceId, "row-2", 1))
                .thenReturn(Optional.empty());

        AssistantContextResult result = service.query(new AssistantContextQuery(
                workspaceId, " query ", null, 5, 1
        ));

        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).assetId()).isEqualTo(approvedAsset);
        assertThat(result.sources().get(0).text()).isEqualTo("canonical context");
    }

    @Test
    void missingStableRowIdIsDerivedFromSegmentIndex() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(search.search("query", workspaceId, null)).thenReturn(new AssistantSearchPage(
                workspaceId, List.of(hit(assetId, null, 7))
        ));
        when(transcripts.findSearchableTranscriptContext(assetId, workspaceId, "segment-7", 0))
                .thenReturn(Optional.of(context(assetId, null, 7, "text")));

        AssistantContextResult result = service.query(new AssistantContextQuery(
                workspaceId, "query", null, 1, 0
        ));

        assertThat(result.sources().get(0).transcriptRowId()).isEqualTo("segment-7");
    }

    @Test
    void invalidBoundsFailBeforeOutboundCalls() {
        UUID workspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> service.query(new AssistantContextQuery(
                workspaceId, "query", null, 0, 1
        ))).isInstanceOf(InvalidAssistantContextRequestException.class);
        assertThatThrownBy(() -> service.query(new AssistantContextQuery(
                workspaceId, "query", null, 1, AssistantContextService.MAX_CONTEXT_WINDOW + 1
        ))).isInstanceOf(InvalidAssistantContextRequestException.class);

        verifyNoInteractions(search, transcripts);
    }

    private AssistantSearchHit hit(UUID assetId, String rowId, Integer segmentIndex) {
        return new AssistantSearchHit(
                assetId, "Indexed title", rowId, segmentIndex, "indexed text", "2026-01-01T00:00:00Z", 2.0
        );
    }

    private AssistantTranscriptContext context(UUID assetId, String rowId, Integer segmentIndex, String text) {
        return new AssistantTranscriptContext(
                assetId,
                "Canonical title",
                rowId,
                segmentIndex,
                1,
                List.of(new AssistantTranscriptSegment(
                        rowId, "video-1", segmentIndex, text, "2026-01-01T00:00:00Z"
                ))
        );
    }
}
