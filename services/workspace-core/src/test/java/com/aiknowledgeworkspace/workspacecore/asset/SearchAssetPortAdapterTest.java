package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetDetails;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;

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
}
