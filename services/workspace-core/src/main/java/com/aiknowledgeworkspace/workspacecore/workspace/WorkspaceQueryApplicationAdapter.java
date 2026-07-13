package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class WorkspaceQueryApplicationAdapter implements WorkspaceQueryApplication {

    private final WorkspaceService workspaceService;

    WorkspaceQueryApplicationAdapter(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public UUID resolveWorkspaceId(UUID requestedWorkspaceId) {
        return workspaceService.resolveWorkspaceOrDefault(requestedWorkspaceId).getId();
    }
}
