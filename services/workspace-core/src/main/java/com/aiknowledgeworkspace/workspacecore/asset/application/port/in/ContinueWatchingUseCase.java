package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingListView;
import java.util.UUID;

/**
 * Bounded read over the current user's playback progress in one Workspace. It reuses the existing
 * playback-progress ownership and never writes.
 */
public interface ContinueWatchingUseCase {

    ContinueWatchingListView listForWorkspace(UUID requestedWorkspaceId);
}
