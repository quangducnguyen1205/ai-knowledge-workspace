package com.aiknowledgeworkspace.workspacecore.workspace.adapter;

import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.internal.WorkspaceService;

import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class WorkspaceQueryApplicationAdapter implements WorkspaceQueryApplication {

    private final WorkspaceService workspaceService;

    WorkspaceQueryApplicationAdapter(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public UUID resolveWorkspaceId(UUID requestedWorkspaceId) {
        return workspaceService.resolveWorkspaceOrDefault(requestedWorkspaceId).getId();
    }

    @Override
    public Workspace resolveWorkspaceOrDefault(UUID requestedWorkspaceId) {
        return workspaceService.resolveWorkspaceOrDefault(requestedWorkspaceId);
    }

    @Override
    public boolean isOwnedByCurrentUser(Workspace workspace) {
        return workspaceService.isOwnedByCurrentUser(workspace);
    }
}
