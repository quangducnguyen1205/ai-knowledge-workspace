package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceView;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        Instant createdAt
) {

    public static WorkspaceResponse from(WorkspaceView workspace) {
        return new WorkspaceResponse(
                workspace.id(),
                workspace.name(),
                workspace.createdAt()
        );
    }
}
