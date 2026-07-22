package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchApplicationService;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchRelevancePolicyTest {

    @Mock
    private WorkspaceAccessUseCase workspaceQueryApplication;

    @Mock
    private SearchAssetQueryPort searchAssetQueryPort;

    @Mock
    private TranscriptSearchQueryPort transcriptSearchQueryPort;

    private SearchApplicationService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchApplicationService(
                workspaceQueryApplication,
                searchAssetQueryPort,
                transcriptSearchQueryPort
        );
    }

    @Test
    void whatIsCodexKeepsCodexMomentsAndEnforcesWorkspaceCaps() {
        UUID workspaceId = UUID.randomUUID();
        List<UUID> eligibleAssetIds = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        UUID ieltsAssetId = eligibleAssetIds.get(5);
        List<TranscriptSearchHit> candidates = new ArrayList<>();
        candidates.add(hit(
                ieltsAssetId,
                "IELTS Speaking Practice",
                "row-ielts",
                0,
                "What is the best way to answer this IELTS speaking prompt?",
                100.0
        ));
        for (int assetIndex = 0; assetIndex < 5; assetIndex++) {
            UUID assetId = eligibleAssetIds.get(assetIndex);
            for (int moment = 0; moment < 4; moment++) {
                candidates.add(hit(
                        assetId,
                        "Codex Course " + assetIndex,
                        "row-" + assetIndex + "-" + moment,
                        moment,
                        "Codex helps with grounded software tasks, example " + moment + ".",
                        90.0 - (assetIndex * 10) - moment
                ));
            }
        }

        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(eligibleAssetIds);
        when(transcriptSearchQueryPort.search(any())).thenReturn(candidates);

        SearchResult response = searchService.search(new SearchQuery("what is codex", workspaceId, null));

        assertThat(response.hits()).hasSize(12);
        assertThat(response.hits().size()).isEqualTo(12);
        assertThat(response.hits())
                .allSatisfy(result -> assertThat(result.text()).containsIgnoringCase("codex"))
                .noneSatisfy(result -> assertThat(result.assetId()).isEqualTo(ieltsAssetId));
        assertThat(response.hits().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        result -> result.assetId(),
                        java.util.stream.Collectors.counting()
                )))
                .allSatisfy((assetId, count) -> assertThat(count).isLessThanOrEqualTo(3));

        ArgumentCaptor<TranscriptSearchQuery> queryCaptor = ArgumentCaptor.forClass(TranscriptSearchQuery.class);
        verify(transcriptSearchQueryPort).search(queryCaptor.capture());
        assertThat(queryCaptor.getValue().query()).isEqualTo("what is codex");
        assertThat(queryCaptor.getValue().meaningfulTerms()).containsExactly("codex");
        assertThat(queryCaptor.getValue().workspaceId()).isEqualTo(workspaceId);
        assertThat(queryCaptor.getValue().eligibleAssetIds()).containsExactlyElementsOf(eligibleAssetIds);
    }

    @Test
    void equalScoresHaveDeterministicSegmentAssetAndRowOrdering() {
        UUID workspaceId = UUID.randomUUID();
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId))
                .thenReturn(List.of(firstAssetId, secondAssetId));
        when(transcriptSearchQueryPort.search(any())).thenReturn(List.of(
                hit(secondAssetId, "Codex", "row-b", 2, "Codex details", 4.0),
                hit(secondAssetId, "Codex", "row-a", 1, "Codex details", 4.0),
                hit(firstAssetId, "Codex", "row-c", 1, "Codex details", 4.0)
        ));

        SearchResult response = searchService.search(new SearchQuery("codex", workspaceId, null));

        assertThat(response.hits())
                .extracting(result -> result.assetId() + ":" + result.transcriptRowId())
                .containsExactly(
                        firstAssetId + ":row-c",
                        secondAssetId + ":row-a",
                        secondAssetId + ":row-b"
                );
    }

    @Test
    void genericOnlyQueryReturnsNoLooseMatches() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));

        SearchResult response = searchService.search(new SearchQuery("what is this", workspaceId, null));

        assertThat(response.hits().size()).isZero();
        assertThat(response.hits()).isEmpty();
        verify(transcriptSearchQueryPort, never()).search(any());
    }

    private TranscriptSearchHit hit(
            UUID assetId,
            String assetTitle,
            String transcriptRowId,
            int segmentIndex,
            String text,
            double score
    ) {
        return new TranscriptSearchHit(
                assetId,
                assetTitle,
                transcriptRowId,
                segmentIndex,
                null,
                null,
                text,
                "2026-07-16T00:00:00Z",
                score
        );
    }
}
