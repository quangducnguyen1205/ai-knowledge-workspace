package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.exception.SearchAssetNotFoundException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContext;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextLoadException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextTarget;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SearchApplicationServiceTest {

    @Mock
    private WorkspaceAccessUseCase workspaces;

    @Mock
    private SearchAssetQueryPort assets;

    @Mock
    private TranscriptSearchQueryPort searchIndex;

    private SearchApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SearchApplicationService(workspaces, assets, searchIndex);
    }

    @Test
    void workspaceSearchUsesOnlyAuthorizedSearchableAssetIds() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(null)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        when(searchIndex.search(any())).thenReturn(List.of(new TranscriptSearchHit(
                assetId, "Lecture", "row-1", 1, 1000L, 2000L,
                "dynamic programming", "2026-01-01T00:00:00Z", 2.0
        )));

        SearchResult result = service.search(new SearchQuery(" dynamic programming ", null, null));

        assertThat(result.workspaceIdFilter()).isEqualTo(workspaceId);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).startMs()).isEqualTo(1000L);
        assertThat(result.hits().get(0).endMs()).isEqualTo(2000L);
        ArgumentCaptor<TranscriptSearchQuery> query = ArgumentCaptor.forClass(TranscriptSearchQuery.class);
        verify(searchIndex).search(query.capture());
        assertThat(query.getValue().eligibleAssetIds()).containsExactly(assetId);
        verify(assets, never()).loadCanonicalContexts(any(), any());
    }

    @Test
    void assetOutsideResolvedWorkspaceIsHiddenAsNotFound() {
        UUID selectedWorkspace = UUID.randomUUID();
        UUID otherWorkspace = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(selectedWorkspace)).thenReturn(selectedWorkspace);
        when(assets.getAuthorizedAssetDetails(assetId))
                .thenReturn(new SearchAssetDetails(assetId, otherWorkspace, true));

        assertThatThrownBy(() -> service.search(new SearchQuery("query", selectedWorkspace, assetId)))
                .isInstanceOf(SearchAssetNotFoundException.class);
        verifyNoInteractions(searchIndex);
    }

    @Test
    void workspaceSearchDiscardsOutOfScopeHitsAndLogsOneBoundedWarning(CapturedOutput output) {
        UUID workspaceId = UUID.randomUUID();
        UUID authorizedAssetId = UUID.randomUUID();
        UUID firstOutOfScopeAssetId = UUID.randomUUID();
        UUID secondOutOfScopeAssetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(authorizedAssetId));
        when(searchIndex.search(any())).thenReturn(List.of(
                hit(firstOutOfScopeAssetId, "private-title-a", "private-row-a", "private transcript target"),
                hit(authorizedAssetId, "Authorized", "allowed-row", "target private result"),
                hit(secondOutOfScopeAssetId, "private-title-b", "private-row-b", "private transcript target")
        ));

        SearchResult result = service.search(new SearchQuery("target-private-query", workspaceId, null));

        assertThat(result.hits())
                .extracting(searchHit -> searchHit.transcriptRowId())
                .containsExactly("allowed-row");
        assertThat(output.getAll())
                .containsOnlyOnce("Discarded 2 out-of-scope search hits for workspace scope")
                .doesNotContain(workspaceId.toString())
                .doesNotContain(firstOutOfScopeAssetId.toString())
                .doesNotContain(secondOutOfScopeAssetId.toString())
                .doesNotContain("target-private-query")
                .doesNotContain("private-title")
                .doesNotContain("private transcript");
    }

    @Test
    void workspaceSearchWithOnlyOutOfScopeHitsReturnsSuccessfulEmptyResult() {
        UUID workspaceId = UUID.randomUUID();
        UUID authorizedAssetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(authorizedAssetId));
        when(searchIndex.search(any())).thenReturn(List.of(
                hit(UUID.randomUUID(), "Other", "other-row", "target result")
        ));

        SearchResult result = service.search(new SearchQuery("target", workspaceId, null));

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void assetScopedSearchDiscardsCandidateFromDifferentAsset() {
        UUID workspaceId = UUID.randomUUID();
        UUID selectedAssetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.getAuthorizedAssetDetails(selectedAssetId))
                .thenReturn(new SearchAssetDetails(selectedAssetId, workspaceId, true));
        when(searchIndex.search(any())).thenReturn(List.of(
                hit(UUID.randomUUID(), "Other", "other-row", "target result")
        ));

        SearchResult result = service.search(new SearchQuery("target", workspaceId, selectedAssetId));

        assertThat(result.assetIdFilter()).isEqualTo(selectedAssetId);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void browserSearchHydratesSelectedHitsInOneBoundedPortCallAndPreservesRanking() {
        UUID workspaceId = UUID.randomUUID();
        UUID firstAssetId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
        UUID secondAssetId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId))
                .thenReturn(List.of(firstAssetId, secondAssetId));
        TranscriptSearchHit first = hit(firstAssetId, "First", "row-1", "matching row one");
        TranscriptSearchHit second = hit(secondAssetId, "Second", "row-2", "matching row two");
        when(searchIndex.search(any())).thenReturn(List.of(first, second));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                context(first, "Previous one.", "matching row one", "Next one."),
                context(second, "Previous two.", "matching row two", "Next two.")
        ));

        SearchResult result = service.search(new SearchQuery("matching row", workspaceId, null, true));

        assertThat(result.hits()).extracting(searchHit -> searchHit.transcriptRowId())
                .containsExactly("row-1", "row-2");
        assertThat(result.hits()).extracting(searchHit -> searchHit.contextSnippet())
                .containsExactly(
                        "Previous one. matching row one Next one.",
                        "Previous two. matching row two Next two."
                );
        assertThat(result.hits()).allSatisfy(searchHit -> assertThat(searchHit.score()).isEqualTo(1.0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SearchCanonicalContextTarget>> targets = ArgumentCaptor.forClass(List.class);
        verify(assets).loadCanonicalContexts(eq(workspaceId), targets.capture());
        assertThat(targets.getValue())
                .extracting(SearchCanonicalContextTarget::assetId)
                .containsExactly(firstAssetId, secondAssetId);
    }

    @Test
    void missingOrStaleCanonicalRowsAreDiscardedWithoutReorderingValidHits() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        TranscriptSearchHit first = hit(assetId, "Lecture", "row-1", "target first");
        TranscriptSearchHit missing = new TranscriptSearchHit(
                assetId, "Lecture", "row-3", 3, 1000L, 2000L,
                "target missing", "2026-07-30T00:00:00Z", 0.9
        );
        TranscriptSearchHit last = new TranscriptSearchHit(
                assetId, "Lecture", "row-5", 5, 1000L, 2000L,
                "target last", "2026-07-30T00:00:00Z", 0.8
        );
        when(searchIndex.search(any())).thenReturn(List.of(first, missing, last));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                context(first, null, "target first", null),
                context(last, null, "target last", null)
        ));

        SearchResult result = service.search(new SearchQuery("target", workspaceId, null, true));

        assertThat(result.hits()).extracting(searchHit -> searchHit.transcriptRowId())
                .containsExactly("row-1", "row-5");
    }

    @Test
    void staleTimingOrMaterialTextChangeDiscardsOnlyAffectedHits() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        TranscriptSearchHit staleTiming = hit(assetId, "Lecture", "row-1", "target timing");
        TranscriptSearchHit staleText = new TranscriptSearchHit(
                assetId, "Lecture", "row-3", 3, 1000L, 2000L,
                "target old text", "2026-07-30T00:00:00Z", 0.9
        );
        when(searchIndex.search(any())).thenReturn(List.of(staleTiming, staleText));
        SearchCanonicalContext timingContext = context(staleTiming, null, "target timing", null);
        SearchCanonicalContextRow changedTiming = new SearchCanonicalContextRow(
                "row-1", 1, 1100L, 2000L, "target timing", "2026-07-30T00:00:00Z"
        );
        SearchCanonicalContext textContext = context(staleText, null, "target new text", null);
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                new SearchCanonicalContext(
                        assetId, "row-1", 1, changedTiming, List.of(changedTiming)
                ),
                textContext
        ));

        SearchResult result = service.search(new SearchQuery("target", workspaceId, null, true));

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void staleSegmentOrCreationIdentityDiscardsOnlyAffectedHits() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        TranscriptSearchHit staleSegment = hit(assetId, "Lecture", "row-1", "target segment");
        TranscriptSearchHit staleCreatedAt = new TranscriptSearchHit(
                assetId, "Lecture", "row-3", 3, 1000L, 2000L,
                "target creation", "2026-07-30T00:00:03Z", 0.9
        );
        SearchCanonicalContextRow changedSegment = new SearchCanonicalContextRow(
                "row-1", 2, 1000L, 2000L, "target segment", staleSegment.createdAt()
        );
        SearchCanonicalContextRow changedCreatedAt = new SearchCanonicalContextRow(
                "row-3", 3, 1000L, 2000L, "target creation", "2026-07-30T01:00:03Z"
        );
        when(searchIndex.search(any())).thenReturn(List.of(staleSegment, staleCreatedAt));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                new SearchCanonicalContext(
                        assetId, "row-1", 1, changedSegment, List.of(changedSegment)
                ),
                new SearchCanonicalContext(
                        assetId, "row-3", 3, changedCreatedAt, List.of(changedCreatedAt)
                )
        ));

        SearchResult result = service.search(new SearchQuery("target", workspaceId, null, true));

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void legacyHitWithoutRowIdIsDiscardedWhenCanonicalRowNowHasAnIdentity() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        TranscriptSearchHit legacy = new TranscriptSearchHit(
                assetId, "Lecture", null, 7, 7000L, 7999L,
                "target legacy", "2026-07-30T00:00:07Z", 1.0
        );
        SearchCanonicalContextRow canonical = new SearchCanonicalContextRow(
                "canonical-row-7", 7, 7000L, 7999L,
                "target legacy", "2026-07-30T00:00:07Z"
        );
        when(searchIndex.search(any())).thenReturn(List.of(legacy));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                new SearchCanonicalContext(
                        assetId, null, 7, canonical, List.of(canonical)
                )
        ));

        SearchResult result = service.search(new SearchQuery("target", workspaceId, null, true));

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void canonicalWhitespaceNormalizationDoesNotRewriteOriginalSearchHit() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        TranscriptSearchHit hit = hit(assetId, "Lecture", "row-1", "target   canonical\ntext");
        when(searchIndex.search(any())).thenReturn(List.of(hit));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                context(hit, null, " target canonical text ", null)
        ));

        SearchResult result = service.search(new SearchQuery("target canonical", workspaceId, null, true));

        assertThat(result.hits()).singleElement().satisfies(searchHit -> {
            assertThat(searchHit.text()).isEqualTo("target   canonical\ntext");
            assertThat(searchHit.contextSnippet()).isEqualTo("target canonical text");
        });
    }

    @Test
    void operationalCanonicalContextFailureRemainsBounded() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        when(searchIndex.search(any())).thenReturn(List.of(hit(
                assetId, "Lecture", "row-1", "target row"
        )));
        when(assets.loadCanonicalContexts(eq(workspaceId), any()))
                .thenThrow(new SearchCanonicalContextLoadException());

        assertThatThrownBy(() -> service.search(new SearchQuery("target", workspaceId, null, true)))
                .isInstanceOf(SearchCanonicalContextLoadException.class)
                .hasMessage("Canonical search context is unavailable");
    }

    @Test
    void oversizedPersistenceContextResponseIsRejectedAsOperationalFailure() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        TranscriptSearchHit hit = hit(assetId, "Lecture", "row-1", "target row");
        SearchCanonicalContextRow matched = new SearchCanonicalContextRow(
                "row-1", 1, 1000L, 2000L, "target row", hit.createdAt()
        );
        when(searchIndex.search(any())).thenReturn(List.of(hit));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of(
                new SearchCanonicalContext(
                        assetId,
                        "row-1",
                        1,
                        matched,
                        List.of(
                                new SearchCanonicalContextRow(
                                        "row-0", 0, null, null, "zero", hit.createdAt()
                                ),
                                matched,
                                new SearchCanonicalContextRow(
                                        "row-2", 2, null, null, "two", hit.createdAt()
                                ),
                                new SearchCanonicalContextRow(
                                        "row-3", 3, null, null, "three", hit.createdAt()
                                )
                        )
                )
        ));

        assertThatThrownBy(() -> service.search(new SearchQuery(
                "target", workspaceId, null, true
        ))).isInstanceOf(SearchCanonicalContextLoadException.class);
    }

    @Test
    void allStaleHitsReturnSuccessfulEmptyResultWithoutRefill() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        when(searchIndex.search(any())).thenReturn(List.of(
                hit(assetId, "Lecture", "row-1", "target row")
        ));
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenReturn(List.of());

        SearchResult result = service.search(new SearchQuery("target", workspaceId, null, true));

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void staleSelectedHitIsNotRefilledFromThirteenthAssetScopedCandidate() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.getAuthorizedAssetDetails(assetId))
                .thenReturn(new SearchAssetDetails(assetId, workspaceId, true));
        List<TranscriptSearchHit> candidates = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            candidates.add(new TranscriptSearchHit(
                    assetId,
                    "Lecture",
                    "row-" + index,
                    index * 2,
                    (long) index * 1000,
                    (long) index * 1000 + 999,
                    "target candidate " + index,
                    "2026-07-30T00:00:00Z",
                    20.0 - index
            ));
        }
        when(searchIndex.search(any())).thenReturn(candidates);
        when(assets.loadCanonicalContexts(eq(workspaceId), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<SearchCanonicalContextTarget> selected = invocation.getArgument(1);
            return selected.stream()
                    .skip(1)
                    .map(target -> {
                        TranscriptSearchHit source = candidates.stream()
                                .filter(candidate -> candidate.transcriptRowId().equals(target.transcriptRowId()))
                                .findFirst()
                                .orElseThrow();
                        return context(source, null, source.text(), null);
                    })
                    .toList();
        });

        SearchResult result = service.search(new SearchQuery(
                "target candidate", workspaceId, assetId, true
        ));

        assertThat(result.hits()).hasSize(11);
        assertThat(result.hits()).extracting(searchHit -> searchHit.transcriptRowId())
                .containsExactly(
                        "row-1", "row-2", "row-3", "row-4", "row-5", "row-6",
                        "row-7", "row-8", "row-9", "row-10", "row-11"
                )
                .doesNotContain("row-12");
    }

    private TranscriptSearchHit hit(
            UUID assetId,
            String title,
            String rowId,
            String text
    ) {
        return new TranscriptSearchHit(
                assetId,
                title,
                rowId,
                1,
                1000L,
                2000L,
                text,
                "2026-07-30T00:00:00Z",
                1.0
        );
    }

    private SearchCanonicalContext context(
            TranscriptSearchHit hit,
            String previousText,
            String canonicalText,
            String nextText
    ) {
        SearchCanonicalContextRow matched = new SearchCanonicalContextRow(
                hit.transcriptRowId(),
                hit.segmentIndex(),
                hit.startMs(),
                hit.endMs(),
                canonicalText,
                hit.createdAt()
        );
        java.util.ArrayList<SearchCanonicalContextRow> rows = new java.util.ArrayList<>();
        if (previousText != null) {
            rows.add(new SearchCanonicalContextRow(
                    "previous-" + hit.transcriptRowId(),
                    hit.segmentIndex() - 1,
                    null,
                    null,
                    previousText,
                    hit.createdAt()
            ));
        }
        rows.add(matched);
        if (nextText != null) {
            rows.add(new SearchCanonicalContextRow(
                    "next-" + hit.transcriptRowId(),
                    hit.segmentIndex() + 1,
                    null,
                    null,
                    nextText,
                    hit.createdAt()
            ));
        }
        return new SearchCanonicalContext(
                hit.assetId(),
                hit.transcriptRowId(),
                hit.segmentIndex(),
                matched,
                rows
        );
    }
}
