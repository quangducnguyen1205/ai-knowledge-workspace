package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByWorkspace_Id(UUID workspaceId, Sort sort);

    List<Asset> findByWorkspaceIsNull(Sort sort);
}
