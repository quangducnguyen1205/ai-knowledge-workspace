package com.aiknowledgeworkspace.workspacecore.workspace.adapter;

import com.aiknowledgeworkspace.workspacecore.workspace.application.internal.WorkspaceService;

import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccessApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class WorkspaceQueryApplicationAdapter implements WorkspaceQueryApplication, WorkspaceAccessApplication {

    private final WorkspaceService workspaceService;

    WorkspaceQueryApplicationAdapter(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public UUID resolveWorkspaceId(UUID requestedWorkspaceId) {
        return workspaceService.resolveWorkspaceOrDefault(requestedWorkspaceId).getId();
    }

    @Override
    public WorkspaceAccess resolveWorkspaceOrDefault(UUID requestedWorkspaceId) {
        Workspace workspace = workspaceService.resolveWorkspaceOrDefault(requestedWorkspaceId);
        return new WorkspaceAccess(workspace.getId(), workspace.getOwnerId());
    }

    @Override
    public boolean isOwnedByCurrentUser(UUID workspaceId) {
        return workspaceService.isOwnedByCurrentUser(workspaceId);
    }
}
