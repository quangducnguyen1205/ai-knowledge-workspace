package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

interface AssetJpaRepository extends JpaRepository<Asset, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from Asset asset where asset.id = :assetId")
    Optional<Asset> findByIdForUpdate(@Param("assetId") UUID assetId);

    List<Asset> findByWorkspaceIdOrderByCreatedAtDescIdDesc(UUID workspaceId);

    List<Asset> findByWorkspaceIdOrderByCreatedAtDescIdDesc(UUID workspaceId, Pageable pageable);

    List<Asset> findByWorkspaceIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID workspaceId, AssetStatus status, Pageable pageable
    );

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, AssetStatus status);

    boolean existsByWorkspaceIdAndYoutubeVideoId(UUID workspaceId, String youtubeVideoId);
}
