package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchAssetPortAdapterTest {

    @Mock
    private AssetTranscriptQueryService transcriptQueryService;

    @Mock
    private DirectProcessingCompatibilityAdapter compatibilityAdapter;

    @Mock
    private AssetSearchabilityService assetSearchabilityService;

    @Test
    void currentIndexingSourceMapsOnlyAssetOwnedReadModels() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(transcriptQueryService.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(
                new AssetIndexingSource(
                        assetId,
                        workspaceId,
                        "Lecture",
                        List.of(new AssetTranscriptRowView(
                                "row-1", "video-1", 0, "canonical", "2026-06-26T00:00:00Z"
                        ))
                )
        ));

        Optional<IndexingAssetSource> result = adapter().findCurrentIndexingSource(assetId);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().transcriptRows()).singleElement()
                .extracting(row -> row.text()).isEqualTo("canonical");
    }

    @Test
    void completedProcessingFallbackStaysInsideCompatibilityAdapter() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(compatibilityAdapter.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(new AssetIndexingSource(assetId, workspaceId, "Lecture", List.of()));

        IndexingAssetSource result = adapter()
                .loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1");

        assertThat(result.assetId()).isEqualTo(assetId);
        verify(compatibilityAdapter).loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1");
    }

    private SearchAssetPortAdapter adapter() {
        return new SearchAssetPortAdapter(
                transcriptQueryService,
                compatibilityAdapter,
                assetSearchabilityService
        );
    }
}
