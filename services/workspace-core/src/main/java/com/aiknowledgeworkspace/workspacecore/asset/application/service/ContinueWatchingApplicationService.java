package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.ResumableAssetPlayback;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.ContinueWatchingUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingItem;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingListView;
import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lists the Assets the current user can continue watching in one Workspace.
 *
 * <p>This is a pure read over the existing playback-progress ownership: it never writes, never
 * changes Asset, transcript, indexing or media state, and reuses the same authorization boundary
 * as the per-Asset progress endpoints. Eligibility deliberately carries no Asset-status rule,
 * because playback progress itself is status independent; the only availability requirement is
 * that the Asset still exists inside the requested owned Workspace.
 */
@Service
public class ContinueWatchingApplicationService implements ContinueWatchingUseCase {

    static final int MAX_ITEMS_PER_WORKSPACE = 12;

    private final AssetPlaybackProgressStore progressStore;
    private final WorkspaceAccessUseCase workspaceAccess;
    private final CurrentUserContext currentUser;

    public ContinueWatchingApplicationService(
            AssetPlaybackProgressStore progressStore,
            WorkspaceAccessUseCase workspaceAccess,
            CurrentUserContext currentUser
    ) {
        this.progressStore = progressStore;
        this.workspaceAccess = workspaceAccess;
        this.currentUser = currentUser;
    }

    @Override
    public ContinueWatchingListView listForWorkspace(UUID requestedWorkspaceId) {
        UUID workspaceId = workspaceAccess.resolveWorkspaceOrDefault(requestedWorkspaceId).workspaceId();
        List<ContinueWatchingItem> items = progressStore
                .findResumable(currentUser.getCurrentUserId(), workspaceId, MAX_ITEMS_PER_WORKSPACE)
                .stream()
                .map(ContinueWatchingApplicationService::toItem)
                .toList();
        return new ContinueWatchingListView(workspaceId, items.size(), MAX_ITEMS_PER_WORKSPACE, items);
    }

    private static ContinueWatchingItem toItem(ResumableAssetPlayback playback) {
        return new ContinueWatchingItem(
                playback.assetId(),
                playback.workspaceId(),
                playback.assetTitle(),
                playback.sourceType() == null ? null : playback.sourceType().name(),
                playback.positionMs(),
                playback.completed(),
                playback.updatedAt()
        );
    }
}
