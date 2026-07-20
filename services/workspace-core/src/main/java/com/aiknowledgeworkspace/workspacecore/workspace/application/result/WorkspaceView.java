package com.aiknowledgeworkspace.workspacecore.workspace.application.result;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceView(UUID id, String name, Instant createdAt) {

    public static WorkspaceView from(Workspace workspace) {
        return new WorkspaceView(workspace.getId(), workspace.getName(), workspace.getCreatedAt());
    }
}
