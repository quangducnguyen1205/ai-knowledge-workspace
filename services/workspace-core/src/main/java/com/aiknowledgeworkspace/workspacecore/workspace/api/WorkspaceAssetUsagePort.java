package com.aiknowledgeworkspace.workspacecore.workspace.api;

import java.util.UUID;

@FunctionalInterface
public interface WorkspaceAssetUsagePort {
    boolean workspaceHasAssets(UUID workspaceId);
}
