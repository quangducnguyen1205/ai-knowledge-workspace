package com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetJpaRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByWorkspaceIdOrderByCreatedAtDescIdDesc(UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);
}
