package com.aiknowledgeworkspace.workspacecore.workspace.application;

import java.util.UUID;

@FunctionalInterface
public interface WorkspaceAssetUsagePort {
    boolean workspaceHasAssets(UUID workspaceId);
}
