package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.assistant.application.exception.InvalidAssistantContextRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.assistant.application.service.AssistantContextApplicationService;
import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantTranscriptContextPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantTranscriptSegment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantContextApplicationServiceTest {

    @Mock
    private AssistantSearchPort search;

    @Mock
    private AssistantTranscriptContextPort transcripts;

    private AssistantContextApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AssistantContextApplicationService(search, transcripts);
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
        assertThat(result.sources().get(0).startMs()).isEqualTo(1000L);
        assertThat(result.sources().get(0).endMs()).isEqualTo(2000L);
        assertThat(result.sources().get(0).citation().startMs()).isEqualTo(1000L);
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
                workspaceId, "query", null, 1, AssistantContextApplicationService.MAX_CONTEXT_WINDOW + 1
        ))).isInstanceOf(InvalidAssistantContextRequestException.class);

        verifyNoInteractions(search, transcripts);
    }

    private AssistantSearchHit hit(UUID assetId, String rowId, Integer segmentIndex) {
        return new AssistantSearchHit(
                assetId, "Indexed title", rowId, segmentIndex, 999L, 1999L,
                "indexed text", "2026-01-01T00:00:00Z", 2.0
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
                        rowId, "video-1", segmentIndex, 1000L, 2000L, text, "2026-01-01T00:00:00Z"
                ))
        );
    }
}
