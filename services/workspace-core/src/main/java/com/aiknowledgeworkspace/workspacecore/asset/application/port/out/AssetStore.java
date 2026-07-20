package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetStore {

    Optional<Asset> findById(UUID assetId);

    List<Asset> findByWorkspaceId(UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    Asset save(Asset asset);

    void delete(Asset asset);
}
