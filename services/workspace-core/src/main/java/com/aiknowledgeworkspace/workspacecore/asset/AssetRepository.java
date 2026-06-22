package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByWorkspace_Id(UUID workspaceId, Sort sort);

    List<Asset> findByWorkspace_IdAndStatus(UUID workspaceId, AssetStatus status, Sort sort);

    long countByWorkspace_Id(UUID workspaceId);
}
