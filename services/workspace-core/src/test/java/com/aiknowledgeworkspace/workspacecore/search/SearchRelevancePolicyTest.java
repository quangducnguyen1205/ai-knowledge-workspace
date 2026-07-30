package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
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
                        moment * 2,
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
                hit(secondAssetId, "Codex", "row-b", 3, "Codex details", 4.0),
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
    void consecutiveRunCollapsesTransitivelyAndEqualScoresUseExistingTieBreak() {
        UUID assetId = UUID.randomUUID();

        SearchResult response = workspaceSearch(List.of(
                targetHit(assetId, "row-22", 22, 10.0),
                targetHit(assetId, "row-20", 20, 10.0),
                targetHit(assetId, "row-21", 21, 10.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("row-20");
    }

    @Test
    void higherRankedMiddleSegmentRepresentsAdjacentRun() {
        UUID assetId = UUID.randomUUID();

        SearchResult response = workspaceSearch(List.of(
                targetHit(assetId, "row-20", 20, 8.0),
                targetHit(assetId, "row-21", 21, 12.0),
                targetHit(assetId, "row-22", 22, 10.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("row-21");
    }

    @Test
    void inputPermutationKeepsRepresentativeAndGlobalOrderDeterministic() {
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<TranscriptSearchHit> firstOrder = List.of(
                targetHit(secondAssetId, "second-5", 5, 9.0),
                targetHit(firstAssetId, "first-21", 21, 12.0),
                targetHit(firstAssetId, "first-30", 30, 8.0),
                targetHit(firstAssetId, "first-20", 20, 10.0),
                targetHit(firstAssetId, "first-22", 22, 11.0)
        );
        List<TranscriptSearchHit> secondOrder = List.of(
                firstOrder.get(4),
                firstOrder.get(3),
                firstOrder.get(2),
                firstOrder.get(1),
                firstOrder.get(0)
        );

        SearchResult first = workspaceSearch(firstOrder);
        SearchResult second = workspaceSearch(secondOrder);

        assertThat(first.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("first-21", "second-5", "first-30");
        assertThat(second.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactlyElementsOf(first.hits().stream()
                        .map(result -> result.transcriptRowId())
                        .toList());
    }

    @Test
    void nonAdjacentIdenticalTextRemainsSeparateMoments() {
        UUID assetId = UUID.randomUUID();

        SearchResult response = workspaceSearch(List.of(
                targetHit(assetId, "row-20", 20, 10.0),
                targetHit(assetId, "row-22", 22, 9.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("row-20", "row-22");
    }

    @Test
    void connectedPairAndSeparatedIndexProduceTwoRepresentatives() {
        UUID assetId = UUID.randomUUID();

        SearchResult response = workspaceSearch(List.of(
                targetHit(assetId, "row-23", 23, 8.0),
                targetHit(assetId, "row-21", 21, 9.0),
                targetHit(assetId, "row-20", 20, 10.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("row-20", "row-23");
    }

    @Test
    void sameSegmentIndexesInDifferentAssetsNeverShareACluster() {
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SearchResult response = workspaceSearch(List.of(
                targetHit(secondAssetId, "second-20", 20, 10.0),
                targetHit(firstAssetId, "first-20", 20, 10.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("first-20", "second-20");
    }

    @Test
    void nullIndexesRemainIndependentAndDuplicateNonNullIndexUsesHighestRankedCandidate() {
        UUID assetId = UUID.randomUUID();

        SearchResult response = workspaceSearch(List.of(
                targetHit(assetId, "null-a", null, 12.0),
                targetHit(assetId, "null-b", null, 11.0),
                targetHit(assetId, "duplicate-lower", 20, 8.0),
                targetHit(assetId, "duplicate-higher", 20, 10.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("null-a", "null-b", "duplicate-higher");
    }

    @Test
    void adjacentDeduplicationRunsBeforeWorkspacePerAssetQuota() {
        UUID assetId = UUID.randomUUID();

        SearchResult response = workspaceSearch(List.of(
                targetHit(assetId, "cluster-20", 20, 20.0),
                targetHit(assetId, "cluster-21", 21, 19.0),
                targetHit(assetId, "cluster-22", 22, 18.0),
                targetHit(assetId, "moment-30", 30, 17.0),
                targetHit(assetId, "moment-40", 40, 16.0),
                targetHit(assetId, "moment-50", 50, 15.0)
        ));

        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("cluster-20", "moment-30", "moment-40");
    }

    @Test
    void assetScopedSearchDeduplicatesMomentsAndUsesPublicLimit() {
        UUID assetId = UUID.randomUUID();
        List<TranscriptSearchHit> candidates = new ArrayList<>();
        candidates.add(targetHit(assetId, "cluster-20", 20, 30.0));
        candidates.add(targetHit(assetId, "cluster-21", 21, 29.0));
        for (int index = 0; index < 13; index++) {
            candidates.add(targetHit(assetId, "moment-" + index, 100 + (index * 2), 20.0 - index));
        }

        SearchResult response = assetSearch(assetId, candidates);

        assertThat(response.hits()).hasSize(12);
        assertThat(response.hits().getFirst().transcriptRowId()).isEqualTo("cluster-20");
        assertThat(response.hits())
                .extracting(result -> result.transcriptRowId())
                .doesNotContain("cluster-21");
    }

    @Test
    void emptyAndSingleCandidateInputsRemainStable() {
        UUID assetId = UUID.randomUUID();

        assertThat(workspaceSearch(List.of(assetId), List.of()).hits()).isEmpty();
        assertThat(workspaceSearch(List.of(targetHit(assetId, "single", 20, 10.0))).hits())
                .extracting(result -> result.transcriptRowId())
                .containsExactly("single");
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

    private SearchResult workspaceSearch(List<TranscriptSearchHit> candidates) {
        List<UUID> assetIds = candidates.stream()
                .map(TranscriptSearchHit::assetId)
                .distinct()
                .toList();
        return workspaceSearch(assetIds, candidates);
    }

    private SearchResult workspaceSearch(
            List<UUID> assetIds,
            List<TranscriptSearchHit> candidates
    ) {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(assetIds);
        if (!assetIds.isEmpty()) {
            when(transcriptSearchQueryPort.search(any())).thenReturn(candidates);
        }
        return searchService.search(new SearchQuery("target", workspaceId, null));
    }

    private SearchResult assetSearch(UUID assetId, List<TranscriptSearchHit> candidates) {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.getAuthorizedAssetDetails(assetId))
                .thenReturn(new SearchAssetDetails(assetId, workspaceId, true));
        when(transcriptSearchQueryPort.search(any())).thenReturn(candidates);
        return searchService.search(new SearchQuery("target", workspaceId, assetId));
    }

    private TranscriptSearchHit targetHit(
            UUID assetId,
            String transcriptRowId,
            Integer segmentIndex,
            double score
    ) {
        return hit(assetId, "Target lecture", transcriptRowId, segmentIndex, "Target moment", score);
    }

    private TranscriptSearchHit hit(
            UUID assetId,
            String assetTitle,
            String transcriptRowId,
            Integer segmentIndex,
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
