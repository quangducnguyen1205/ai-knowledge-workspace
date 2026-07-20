package com.aiknowledgeworkspace.workspacecore.workspace.api;

import java.util.UUID;

public interface WorkspaceAccessUseCase {

    WorkspaceAccess resolveWorkspaceOrDefault(UUID requestedWorkspaceId);

    UUID resolveWorkspaceId(UUID requestedWorkspaceId);

    boolean isOwnedByCurrentUser(UUID workspaceId);
}
