package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetWorkspaceUsageService {

    private final AssetRepository assetRepository;

    public AssetWorkspaceUsageService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public boolean workspaceHasAssets(UUID workspaceId) {
        return assetRepository.countByWorkspace_Id(workspaceId) > 0;
    }
}
