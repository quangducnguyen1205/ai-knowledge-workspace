package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.ResumableAssetPlayback;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingItem;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingListView;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.WorkspaceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContinueWatchingApplicationServiceTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOREIGN_WORKSPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USER_ID = "user-1";
    private static final Instant BASE = Instant.parse("2026-07-30T08:00:00Z");

    private final AssetPlaybackProgressStore progressStore = mock(AssetPlaybackProgressStore.class);
    private final WorkspaceAccessUseCase workspaceAccess = mock(WorkspaceAccessUseCase.class);
    private final CurrentUserContext currentUser = mock(CurrentUserContext.class);
    private final ContinueWatchingApplicationService service =
            new ContinueWatchingApplicationService(progressStore, workspaceAccess, currentUser);

    @Test
    void listsTheCurrentUsersResumableAssetsInTheResolvedWorkspace() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(List.of(
                playback(uuid(2), "Second lecture", AssetSourceType.YOUTUBE, 45_000, BASE),
                playback(uuid(1), "First lecture", AssetSourceType.UPLOAD, 12_000, BASE.minusSeconds(60))
        ));

        ContinueWatchingListView view = service.listForWorkspace(WORKSPACE_ID);

        assertThat(view.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(view.itemCount()).isEqualTo(2);
        assertThat(view.maxItems()).isEqualTo(12);
        assertThat(view.items()).extracting(ContinueWatchingItem::assetTitle)
                .containsExactly("Second lecture", "First lecture");
        assertThat(view.items().getFirst()).satisfies(item -> {
            assertThat(item.assetId()).isEqualTo(uuid(2));
            assertThat(item.workspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(item.sourceType()).isEqualTo("YOUTUBE");
            assertThat(item.positionMs()).isEqualTo(45_000);
            assertThat(item.completed()).isFalse();
            assertThat(item.updatedAt()).isEqualTo(BASE);
        });
    }

    @Test
    void theCurrentUserIdentityIsAlwaysTheStoreFilter() {
        currentUserIs("someone-else");
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(anyString(), any(), anyInt())).thenReturn(List.of());

        service.listForWorkspace(WORKSPACE_ID);

        verify(progressStore).findResumable("someone-else", WORKSPACE_ID, 12);
        verify(progressStore, never()).findResumable(USER_ID, WORKSPACE_ID, 12);
    }

    @Test
    void theResolvedWorkspaceIsAlwaysTheStoreFilter() {
        currentUserIs(USER_ID);
        when(workspaceAccess.resolveWorkspaceOrDefault(WORKSPACE_ID))
                .thenReturn(new WorkspaceAccess(WORKSPACE_ID, USER_ID));
        when(progressStore.findResumable(anyString(), any(), anyInt())).thenReturn(List.of());

        service.listForWorkspace(WORKSPACE_ID);

        verify(progressStore).findResumable(USER_ID, WORKSPACE_ID, 12);
        verify(progressStore, never()).findResumable(USER_ID, FOREIGN_WORKSPACE_ID, 12);
    }

    @Test
    void aForeignOrUnknownWorkspaceIsRejectedBeforeAnyProgressRead() {
        currentUserIs(USER_ID);
        when(workspaceAccess.resolveWorkspaceOrDefault(FOREIGN_WORKSPACE_ID))
                .thenThrow(new WorkspaceNotFoundException(FOREIGN_WORKSPACE_ID));

        assertThatThrownBy(() -> service.listForWorkspace(FOREIGN_WORKSPACE_ID))
                .isInstanceOf(WorkspaceNotFoundException.class);
        verifyNoInteractions(progressStore);
    }

    @Test
    void anOmittedWorkspaceUsesTheResolvedDefaultWorkspace() {
        currentUserIs(USER_ID);
        when(workspaceAccess.resolveWorkspaceOrDefault(null))
                .thenReturn(new WorkspaceAccess(WORKSPACE_ID, USER_ID));
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(List.of());

        assertThat(service.listForWorkspace(null).workspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void theServerOwnedMaximumIsTwelveAndIsRequestedFromTheStore() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(anyString(), any(), anyInt())).thenReturn(List.of());

        ContinueWatchingListView view = service.listForWorkspace(WORKSPACE_ID);

        assertThat(ContinueWatchingApplicationService.MAX_ITEMS_PER_WORKSPACE).isEqualTo(12);
        assertThat(view.maxItems()).isEqualTo(12);
        verify(progressStore).findResumable(USER_ID, WORKSPACE_ID, 12);
    }

    @Test
    void theStoreOrderIsPreservedExactlyRatherThanReSortedInJava() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        List<ResumableAssetPlayback> stored = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            stored.add(playback(uuid(index), "Asset " + index, AssetSourceType.UPLOAD,
                    1_000L * (index + 1), BASE.minusSeconds(index)));
        }
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(stored);

        assertThat(service.listForWorkspace(WORKSPACE_ID).items())
                .extracting(ContinueWatchingItem::assetId)
                .containsExactlyElementsOf(stored.stream().map(ResumableAssetPlayback::assetId).toList());
    }

    @Test
    void anEmptyResultIsAWellFormedEmptyList() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(List.of());

        ContinueWatchingListView view = service.listForWorkspace(WORKSPACE_ID);

        assertThat(view.items()).isEmpty();
        assertThat(view.itemCount()).isZero();
        assertThat(view.workspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void listingNeverWritesOrDeletesProgress() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(List.of(
                playback(uuid(1), "Lecture", AssetSourceType.UPLOAD, 12_000, BASE)
        ));

        service.listForWorkspace(WORKSPACE_ID);

        verify(progressStore, never()).upsert(any(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean(), any());
        verify(progressStore, never()).deleteForAsset(any());
        verify(progressStore, never()).find(any(), anyString());
    }

    @Test
    void aMissingSourceTypeIsReportedAsNullRatherThanGuessed() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(List.of(
                new ResumableAssetPlayback(uuid(1), WORKSPACE_ID, "Lecture", null, 12_000, false, BASE)
        ));

        assertThat(service.listForWorkspace(WORKSPACE_ID).items().getFirst().sourceType()).isNull();
    }

    @Test
    void presentationDataComesFromTheProjectionRatherThanAStoredSnapshot() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(progressStore.findResumable(USER_ID, WORKSPACE_ID, 12)).thenReturn(List.of(
                playback(uuid(1), "Renamed after saving progress", AssetSourceType.YOUTUBE, 30_000, BASE)
        ));

        assertThat(service.listForWorkspace(WORKSPACE_ID).items().getFirst()).satisfies(item -> {
            assertThat(item.assetTitle()).isEqualTo("Renamed after saving progress");
            assertThat(item.sourceType()).isEqualTo("YOUTUBE");
        });
    }

    private void currentUserIs(String userId) {
        when(currentUser.getCurrentUserId()).thenReturn(userId);
    }

    private void workspaceResolvesTo(UUID workspaceId) {
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, USER_ID));
    }

    private ResumableAssetPlayback playback(
            UUID assetId, String title, AssetSourceType sourceType, long positionMs, Instant updatedAt
    ) {
        return new ResumableAssetPlayback(assetId, WORKSPACE_ID, title, sourceType, positionMs, false, updatedAt);
    }

    private static UUID uuid(int seed) {
        return UUID.fromString(String.format("aaaaaaaa-aaaa-aaaa-aaaa-%012d", seed));
    }
}
