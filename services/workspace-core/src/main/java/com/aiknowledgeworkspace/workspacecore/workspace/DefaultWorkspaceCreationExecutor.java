package com.aiknowledgeworkspace.workspacecore.workspace;

@FunctionalInterface
public interface DefaultWorkspaceCreationExecutor {

    Workspace create(Workspace workspace);
}
