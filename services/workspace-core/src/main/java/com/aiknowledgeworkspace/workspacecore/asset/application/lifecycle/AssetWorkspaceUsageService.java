package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetWorkspaceUsageService {

    private final AssetStore assetStore;

    public AssetWorkspaceUsageService(AssetStore assetStore) {
        this.assetStore = assetStore;
    }

    @Transactional(readOnly = true)
    public boolean workspaceHasAssets(UUID workspaceId) {
        return assetStore.countByWorkspaceId(workspaceId) > 0;
    }
}
