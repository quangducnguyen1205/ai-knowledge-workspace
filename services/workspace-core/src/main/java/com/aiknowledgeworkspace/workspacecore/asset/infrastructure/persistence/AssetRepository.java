package com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByWorkspaceId(UUID workspaceId, Sort sort);

    List<Asset> findByWorkspaceIdAndStatus(UUID workspaceId, AssetStatus status, Sort sort);

    long countByWorkspaceId(UUID workspaceId);
}
