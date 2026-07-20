package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetWorkspaceUsageService;

import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAssetUsagePort;
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
