package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;

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
        return assetRepository.countByWorkspaceId(workspaceId) > 0;
    }
}
