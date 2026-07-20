package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AssetPersistenceAdapter implements AssetStore {

    private final AssetJpaRepository assetRepository;

    AssetPersistenceAdapter(AssetJpaRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public Optional<Asset> findById(UUID assetId) {
        return assetRepository.findById(assetId);
    }

    @Override
    public List<Asset> findByWorkspaceId(UUID workspaceId) {
        return assetRepository.findByWorkspaceIdOrderByCreatedAtDescIdDesc(workspaceId);
    }

    @Override
    public long countByWorkspaceId(UUID workspaceId) {
        return assetRepository.countByWorkspaceId(workspaceId);
    }

    @Override
    public Asset save(Asset asset) {
        return assetRepository.save(asset);
    }

    @Override
    public void delete(Asset asset) {
        assetRepository.delete(asset);
    }
}
