package com.aiknowledgeworkspace.workspacecore.workspace.application.service;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;

@FunctionalInterface
public interface DefaultWorkspaceCreationExecutor {

    Workspace create(Workspace workspace);
}
