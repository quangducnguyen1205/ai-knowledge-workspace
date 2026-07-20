package com.aiknowledgeworkspace.workspacecore.workspace.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.workspace.application.result.WorkspaceView;
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
