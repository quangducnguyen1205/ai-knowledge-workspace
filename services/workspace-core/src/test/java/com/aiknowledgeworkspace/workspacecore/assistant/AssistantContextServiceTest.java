package com.aiknowledgeworkspace.workspacecore.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptContextResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.common.identity.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import com.aiknowledgeworkspace.workspacecore.search.SearchResponse;
import com.aiknowledgeworkspace.workspacecore.search.SearchResultResponse;
import com.aiknowledgeworkspace.workspacecore.search.SearchService;
import com.aiknowledgeworkspace.workspacecore.search.TranscriptSearchIndexClient;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceProperties;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceRepository;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
class AssistantContextServiceTest {

    @Mock
    private SearchService searchService;

    @Mock
    private AssetService assetService;

    private AssistantContextService assistantContextService;

    @BeforeEach
    void setUp() {
        assistantContextService = new AssistantContextService(searchService, assetService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void buildContextReturnsBoundedContextSourcesForSearchableAssets() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        AssistantContextRequest request = new AssistantContextRequest(
                workspaceId,
                "  dynamic programming  ",
                null,
                5,
                1
        );
        when(searchService.search("dynamic programming", workspaceId, null))
                .thenReturn(searchResponse(workspaceId, result(assetId, "row-2", 2)));
        when(assetService.getAsset(assetId)).thenReturn(asset(assetId, workspaceId, AssetStatus.SEARCHABLE, "Lecture 2"));
        when(assetService.getAssetTranscriptContext(assetId, "row-2", 1))
                .thenReturn(new AssetTranscriptContextResponse(
                        assetId,
                        "row-2",
                        2,
                        1,
                        List.of(
                                row("row-1", 1, "first context row"),
                                row("row-2", 2, "selected context row"),
                                row("row-3", 3, "third context row")
                        )
                ));

        AssistantContextResponse response = assistantContextService.buildContext(request);

        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.query()).isEqualTo("dynamic programming");
        assertThat(response.sources()).hasSize(1);
        AssistantContextSourceResponse source = response.sources().get(0);
        assertThat(source.assetId()).isEqualTo(assetId);
        assertThat(source.assetTitle()).isEqualTo("Lecture 2");
        assertThat(source.transcriptRowId()).isEqualTo("row-2");
        assertThat(source.segmentIndex()).isEqualTo(2);
        assertThat(source.createdAt()).isEqualTo("2026-06-25T00:00:02Z");
        assertThat(source.text()).isEqualTo("first context row\nselected context row\nthird context row");
        assertThat(source.citation()).isEqualTo(new AssistantCitationResponse(assetId, "row-2", 2));
        verify(assetService).getAssetTranscriptContext(assetId, "row-2", 1);
    }

    @Test
    void emptyValidSearchReturnsEmptySources() {
        UUID workspaceId = UUID.randomUUID();
        when(searchService.search("missing", workspaceId, null))
                .thenReturn(new SearchResponse("missing", workspaceId, null, 0, List.of()));

        AssistantContextResponse response = assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "missing",
                null,
                null,
                null
        ));

        assertThat(response.sources()).isEmpty();
        verifyNoMoreInteractions(assetService);
    }

    @Test
    void unknownOrNonOwnedWorkspacePropagatesExistingNotFoundConvention() {
        UUID workspaceId = UUID.randomUUID();
        when(searchService.search("query", workspaceId, null))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "query",
                null,
                null,
                null
        ))).isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void assetFromAnotherWorkspacePropagatesExistingOwnershipSafeNotFoundConvention() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(searchService.search("query", workspaceId, assetId))
                .thenThrow(new AssetNotFoundException());

        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "query",
                assetId,
                null,
                null
        ))).isInstanceOf(AssetNotFoundException.class);
    }

    @Test
    void staleSearchHitForNonSearchableAssetIsNotExposed() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(searchService.search("stale", workspaceId, null))
                .thenReturn(searchResponse(workspaceId, result(assetId, "row-1", 1)));
        when(assetService.getAsset(assetId))
                .thenReturn(asset(assetId, workspaceId, AssetStatus.TRANSCRIPT_READY, "Lecture"));

        AssistantContextResponse response = assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "stale",
                null,
                null,
                null
        ));

        assertThat(response.sources()).isEmpty();
        verify(assetService, never()).getAssetTranscriptContext(any(), any(), any());
    }

    @Test
    void staleSearchHitFromAnotherWorkspaceIsNotExposed() {
        UUID requestedWorkspaceId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(searchService.search("stale", requestedWorkspaceId, null))
                .thenReturn(searchResponse(requestedWorkspaceId, result(assetId, "row-1", 1)));
        when(assetService.getAsset(assetId))
                .thenReturn(asset(assetId, otherWorkspaceId, AssetStatus.SEARCHABLE, "Other Lecture"));

        AssistantContextResponse response = assistantContextService.buildContext(new AssistantContextRequest(
                requestedWorkspaceId,
                "stale",
                null,
                null,
                null
        ));

        assertThat(response.sources()).isEmpty();
        verify(assetService, never()).getAssetTranscriptContext(any(), any(), any());
    }

    @Test
    void maxSourcesIsHonoredInSearchRankingOrder() {
        UUID workspaceId = UUID.randomUUID();
        UUID firstAssetId = UUID.randomUUID();
        UUID secondAssetId = UUID.randomUUID();
        when(searchService.search("ranked", workspaceId, null))
                .thenReturn(new SearchResponse(
                        "ranked",
                        workspaceId,
                        null,
                        2,
                        List.of(result(firstAssetId, "row-1", 1), result(secondAssetId, "row-2", 2))
                ));
        when(assetService.getAsset(firstAssetId))
                .thenReturn(asset(firstAssetId, workspaceId, AssetStatus.SEARCHABLE, "First"));
        when(assetService.getAssetTranscriptContext(firstAssetId, "row-1", 1))
                .thenReturn(new AssetTranscriptContextResponse(
                        firstAssetId,
                        "row-1",
                        1,
                        1,
                        List.of(row("row-1", 1, "first hit"))
                ));

        AssistantContextResponse response = assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "ranked",
                null,
                1,
                1
        ));

        assertThat(response.sources()).extracting(AssistantContextSourceResponse::assetId)
                .containsExactly(firstAssetId);
        verify(assetService, never()).getAsset(secondAssetId);
    }

    @Test
    void contextWindowZeroReturnsOnlyTheCanonicalHitRow() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(searchService.search("exact", workspaceId, assetId))
                .thenReturn(new SearchResponse(
                        "exact",
                        workspaceId,
                        assetId,
                        1,
                        List.of(result(assetId, "row-2", 2))
                ));
        when(assetService.getAsset(assetId))
                .thenReturn(asset(assetId, workspaceId, AssetStatus.SEARCHABLE, "Lecture"));
        when(assetService.getAssetTranscript(assetId))
                .thenReturn(List.of(
                        row("row-1", 1, "neighbor"),
                        row("row-2", 2, "hit only"),
                        row("row-3", 3, "other neighbor")
                ));

        AssistantContextResponse response = assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "exact",
                assetId,
                5,
                0
        ));

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).text()).isEqualTo("hit only");
        verify(assetService, never()).getAssetTranscriptContext(any(), any(), any());
    }

    @Test
    void duplicateCitationsAreRemovedFromOneResponse() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(searchService.search("duplicate", workspaceId, null))
                .thenReturn(new SearchResponse(
                        "duplicate",
                        workspaceId,
                        null,
                        2,
                        List.of(result(assetId, "row-1", 1), result(assetId, "row-1", 1))
                ));
        when(assetService.getAsset(assetId))
                .thenReturn(asset(assetId, workspaceId, AssetStatus.SEARCHABLE, "Lecture"));
        when(assetService.getAssetTranscriptContext(assetId, "row-1", 1))
                .thenReturn(new AssetTranscriptContextResponse(
                        assetId,
                        "row-1",
                        1,
                        1,
                        List.of(row("row-1", 1, "same hit"))
                ));

        AssistantContextResponse response = assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "duplicate",
                null,
                5,
                1
        ));

        assertThat(response.sources()).hasSize(1);
    }

    @Test
    void validationRejectsInvalidRequestValues() {
        UUID workspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> assistantContextService.buildContext(null))
                .isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("Request body is required");
        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                null,
                "query",
                null,
                null,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("workspaceId is required");
        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "   ",
                null,
                null,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("query is required");
        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "a".repeat(501),
                null,
                null,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("query must be at most 500 characters");
        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "query",
                null,
                0,
                null
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("maxSources must be between 1 and 10");
        assertThatThrownBy(() -> assistantContextService.buildContext(new AssistantContextRequest(
                workspaceId,
                "query",
                null,
                null,
                -1
        ))).isInstanceOf(InvalidAssistantContextRequestException.class)
                .hasMessage("contextWindow must be between 0 and 5");
    }

    @Test
    void unauthenticatedCallerUsesExistingCurrentUserFailurePath() {
        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        currentUserProperties.setDevFallbackEnabled(false);
        CurrentUserService currentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                new WorkspaceProperties(),
                currentUserService
        );
        SearchService realSearchService = new SearchService(
                workspaceService,
                assetService,
                assetRepository,
                mock(TranscriptSearchIndexClient.class)
        );
        AssistantContextService realAssistantContextService = new AssistantContextService(realSearchService, assetService);

        assertThatThrownBy(() -> realAssistantContextService.buildContext(new AssistantContextRequest(
                UUID.randomUUID(),
                "query",
                null,
                null,
                null
        ))).isInstanceOf(AuthenticationRequiredException.class);
    }

    private SearchResponse searchResponse(UUID workspaceId, SearchResultResponse result) {
        return new SearchResponse(result == null ? "" : "query", workspaceId, null, result == null ? 0 : 1, List.of(result));
    }

    private SearchResultResponse result(UUID assetId, String transcriptRowId, Integer segmentIndex) {
        return new SearchResultResponse(
                assetId,
                "Indexed Lecture",
                transcriptRowId,
                segmentIndex,
                "indexed text",
                "2026-06-25T00:00:00Z",
                2.5
        );
    }

    private Asset asset(UUID assetId, UUID workspaceId, AssetStatus status, String title) {
        Asset asset = new Asset("lecture.mp4", title, status, new Workspace(workspaceId, "Workspace", "user-1", false));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowResponse row(String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowResponse(
                id,
                "video-1",
                segmentIndex,
                text,
                "2026-06-25T00:00:0" + segmentIndex + "Z"
        );
    }
}
