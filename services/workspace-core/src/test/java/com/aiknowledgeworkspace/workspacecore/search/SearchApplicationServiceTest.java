package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.exception.SearchAssetNotFoundException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchApplicationService;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
}
