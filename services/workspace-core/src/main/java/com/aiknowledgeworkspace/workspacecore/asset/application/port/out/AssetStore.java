package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetStore {

    Optional<Asset> findById(UUID assetId);

    Optional<Asset> findByIdForUpdate(UUID assetId);

    List<Asset> findByWorkspaceId(UUID workspaceId);

    /**
     * One page of a workspace's Assets, newest first, applied by the database. A null status means
     * every status. Page and size are zero-based and already validated by the caller.
     */
    List<Asset> findWorkspacePage(UUID workspaceId, AssetStatus status, int page, int size);

    long countByWorkspaceId(UUID workspaceId);

    long countWorkspaceAssets(UUID workspaceId, AssetStatus status);

    boolean existsByWorkspaceIdAndYoutubeVideoId(UUID workspaceId, String youtubeVideoId);

    Asset save(Asset asset);

    Asset saveYoutube(Asset asset);

    void delete(Asset asset);
}
