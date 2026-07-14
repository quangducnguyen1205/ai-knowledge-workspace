package com.aiknowledgeworkspace.workspacecore.workspace.application;

import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.UUID;

public interface WorkspaceQueryApplication {
    UUID resolveWorkspaceId(UUID requestedWorkspaceId);

    default Workspace resolveWorkspaceOrDefault(UUID requestedWorkspaceId) {
        throw new UnsupportedOperationException("Workspace resolution is not available");
    }

    default boolean isOwnedByCurrentUser(Workspace workspace) {
        return true;
    }
}
