package com.aiknowledgeworkspace.workspacecore.search.adapter.in.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchApplicationService;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantSearchPortAdapterTest {

    @Mock
    private WorkspaceAccessUseCase workspaces;

    @Mock
    private SearchAssetQueryPort assets;

    @Mock
    private TranscriptSearchQueryPort searchIndex;

    @Test
    void assistantSearchExplicitlySkipsCanonicalSnippetHydration() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaces.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(assets.findSearchableAssetIdsInWorkspace(workspaceId)).thenReturn(List.of(assetId));
        when(searchIndex.search(any())).thenReturn(List.of(new TranscriptSearchHit(
                assetId,
                "Lecture",
                "row-1",
                1,
                1000L,
                2000L,
                "canonical target",
                "2026-07-30T00:00:00Z",
                1.0
        )));
        AssistantSearchPortAdapter adapter = new AssistantSearchPortAdapter(
                new SearchApplicationService(workspaces, assets, searchIndex)
        );

        var result = adapter.search("canonical target", workspaceId, null);

        assertThat(result.results()).singleElement().satisfies(hit -> {
            assertThat(hit.transcriptRowId()).isEqualTo("row-1");
            assertThat(hit.startMs()).isEqualTo(1000L);
        });
        verify(assets, never()).loadCanonicalContexts(any(), any());
    }
}
