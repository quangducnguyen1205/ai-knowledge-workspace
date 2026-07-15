package com.aiknowledgeworkspace.workspacecore.workspace.application;

import java.util.UUID;

public interface WorkspaceQueryApplication {
    UUID resolveWorkspaceId(UUID requestedWorkspaceId);
}
