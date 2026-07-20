package com.aiknowledgeworkspace.workspacecore.workspace.application.service;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;

import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkspaceAccessPolicy {

    private final CurrentUserContext currentUserService;

    public WorkspaceAccessPolicy(CurrentUserContext currentUserService) {
        this.currentUserService = currentUserService;
    }

    public boolean isOwnedByCurrentUser(Workspace workspace) {
        return isOwnedBy(workspace, currentUserService.getCurrentUserId());
    }

    public boolean isOwnedBy(Workspace workspace, String userId) {
        return workspace != null
                && StringUtils.hasText(userId)
                && StringUtils.hasText(workspace.getOwnerId())
                && userId.equals(workspace.getOwnerId());
    }
}
