package com.aiknowledgeworkspace.workspacecore.workspace.application;

import java.util.UUID;

public interface WorkspaceAccessApplication {

    WorkspaceAccess resolveWorkspaceOrDefault(UUID requestedWorkspaceId);

    boolean isOwnedByCurrentUser(UUID workspaceId);
}
