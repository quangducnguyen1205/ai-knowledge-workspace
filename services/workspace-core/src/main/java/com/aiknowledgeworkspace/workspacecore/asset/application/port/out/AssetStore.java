package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetStore {

    Optional<Asset> findById(UUID assetId);

    Optional<Asset> findByIdForUpdate(UUID assetId);

    List<Asset> findByWorkspaceId(UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceIdAndYoutubeVideoId(UUID workspaceId, String youtubeVideoId);

    Asset save(Asset asset);

    Asset saveYoutube(Asset asset);

    void delete(Asset asset);
}
