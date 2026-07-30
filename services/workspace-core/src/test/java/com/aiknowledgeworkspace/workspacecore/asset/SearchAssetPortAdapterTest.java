package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetDetails;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.CanonicalTranscriptContextReadException;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module.SearchAssetPortAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextLoadException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextTarget;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchAssetPortAdapterTest {

    @Mock
    private AssetTranscriptQueryService transcriptQueries;

    @Mock
    private AssetSearchabilityService searchability;

    private SearchAssetPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SearchAssetPortAdapter(transcriptQueries, searchability);
    }

    @Test
    void authorizedIndexingSourceUsesOnlyCanonicalTranscriptState() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(transcriptQueries.getAuthorizedAssetDetails(assetId)).thenReturn(
                new AssetDetails(assetId, workspaceId, "Lecture", AssetStatus.TRANSCRIPT_READY)
        );
        when(transcriptQueries.loadUsableSnapshot(assetId)).thenReturn(List.of(
                new AssetTranscriptRowView(
                        "row-1", "video-1", 1, 1000L, 2000L, "canonical", "2026-01-01T00:00:00Z"
                )
        ));

        IndexingAssetSource source = adapter.loadAuthorizedIndexingSource(assetId);

        assertThat(source.assetId()).isEqualTo(assetId);
        assertThat(source.transcriptRows()).extracting(row -> row.text()).containsExactly("canonical");
        assertThat(source.transcriptRows()).singleElement().satisfies(row -> {
            assertThat(row.startMs()).isEqualTo(1000L);
            assertThat(row.endMs()).isEqualTo(2000L);
        });
    }

    @Test
    void assetNotFoundIsTranslatedToSearchModuleBoundaryException() {
        UUID assetId = UUID.randomUUID();
        when(transcriptQueries.getAuthorizedAssetDetails(assetId)).thenThrow(new AssetNotFoundException());

        assertThatThrownBy(() -> adapter.loadAuthorizedIndexingSource(assetId))
                .isInstanceOf(SearchAssetUnavailableException.class);
    }

    @Test
    void lifecycleMutationDelegatesToAssetOwner() {
        UUID assetId = UUID.randomUUID();

        adapter.markSearchable(assetId);

        verify(searchability).markSearchable(assetId);
    }

    @Test
    void canonicalContextTargetsAreCoalescedAndGroupedOncePerAsset() {
        UUID workspaceId = UUID.randomUUID();
        UUID firstAssetId = UUID.randomUUID();
        UUID secondAssetId = UUID.randomUUID();
        SearchCanonicalContextTarget first = new SearchCanonicalContextTarget(
                firstAssetId, "row-1", 1
        );
        SearchCanonicalContextTarget second = new SearchCanonicalContextTarget(
                secondAssetId, null, 2
        );
        SearchCanonicalContextTarget third = new SearchCanonicalContextTarget(
                firstAssetId, "row-3", 3
        );
        CanonicalTranscriptContextWindow firstWindow = window("row-1", 1, "one");
        CanonicalTranscriptContextWindow secondWindow = window(null, 2, "two");
        CanonicalTranscriptContextWindow thirdWindow = window("row-3", 3, "three");
        when(transcriptQueries.findSearchableTranscriptContexts(
                firstAssetId,
                workspaceId,
                List.of(
                        new CanonicalTranscriptContextTarget("row-1", 1),
                        new CanonicalTranscriptContextTarget("row-3", 3)
                )
        )).thenReturn(List.of(firstWindow, thirdWindow));
        when(transcriptQueries.findSearchableTranscriptContexts(
                secondAssetId,
                workspaceId,
                List.of(new CanonicalTranscriptContextTarget(null, 2))
        )).thenReturn(List.of(secondWindow));

        var contexts = adapter.loadCanonicalContexts(
                workspaceId,
                List.of(first, second, third, first)
        );

        assertThat(contexts).extracting(context -> context.assetId())
                .containsExactly(firstAssetId, secondAssetId, firstAssetId);
        assertThat(contexts).extracting(context -> context.matchedRow().text())
                .containsExactly("one", "two", "three");
        verify(transcriptQueries).findSearchableTranscriptContexts(
                firstAssetId,
                workspaceId,
                List.of(
                        new CanonicalTranscriptContextTarget("row-1", 1),
                        new CanonicalTranscriptContextTarget("row-3", 3)
                )
        );
        verify(transcriptQueries).findSearchableTranscriptContexts(
                secondAssetId,
                workspaceId,
                List.of(new CanonicalTranscriptContextTarget(null, 2))
        );
    }

    @Test
    void persistenceContextFailureIsTranslatedWithoutIntegrationDetails() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(transcriptQueries.findSearchableTranscriptContexts(
                assetId,
                workspaceId,
                List.of(new CanonicalTranscriptContextTarget("row-1", 1))
        )).thenThrow(new CanonicalTranscriptContextReadException());

        assertThatThrownBy(() -> adapter.loadCanonicalContexts(
                workspaceId,
                List.of(new SearchCanonicalContextTarget(assetId, "row-1", 1))
        )).isInstanceOf(SearchCanonicalContextLoadException.class)
                .hasMessage("Canonical search context is unavailable");
    }

    @Test
    void moreThanTwelveContextTargetsAreRejectedBeforeAssetAccess() {
        UUID workspaceId = UUID.randomUUID();
        List<SearchCanonicalContextTarget> targets = java.util.stream.IntStream.range(0, 13)
                .mapToObj(index -> new SearchCanonicalContextTarget(
                        UUID.randomUUID(), "row-" + index, index
                ))
                .toList();

        assertThatThrownBy(() -> adapter.loadCanonicalContexts(workspaceId, targets))
                .isInstanceOf(SearchCanonicalContextLoadException.class);
        org.mockito.Mockito.verifyNoInteractions(transcriptQueries);
    }

    private CanonicalTranscriptContextWindow window(String rowId, int segmentIndex, String text) {
        CanonicalTranscriptContextRow matched = new CanonicalTranscriptContextRow(
                rowId, segmentIndex, null, null, text, "2026-07-30T00:00:00Z"
        );
        return new CanonicalTranscriptContextWindow(
                rowId, segmentIndex, matched, List.of(matched)
        );
    }
}
