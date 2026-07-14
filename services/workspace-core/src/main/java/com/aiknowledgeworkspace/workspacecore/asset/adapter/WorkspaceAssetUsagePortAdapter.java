package com.aiknowledgeworkspace.workspacecore.asset.adapter;

import com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle.AssetWorkspaceUsageService;

import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAssetUsagePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class WorkspaceAssetUsagePortAdapter implements WorkspaceAssetUsagePort {

    private final AssetWorkspaceUsageService delegate;

    WorkspaceAssetUsagePortAdapter(AssetWorkspaceUsageService delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean workspaceHasAssets(UUID workspaceId) {
        return delegate.workspaceHasAssets(workspaceId);
    }
}
