package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SavedMomentJpaRepository extends JpaRepository<SavedMomentEntry, UUID> {

    Optional<SavedMomentEntry> findByUserIdAndAssetIdAndTranscriptRowId(
            String userId, UUID assetId, String transcriptRowId
    );

    Optional<SavedMomentEntry> findByIdAndUserId(UUID id, String userId);

    List<SavedMomentEntry> findByUserIdAndWorkspaceIdOrderBySavedAtDescIdDesc(
            String userId, UUID workspaceId, Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SavedMomentEntry entry where entry.assetId = :assetId")
    void deleteByAssetId(@Param("assetId") UUID assetId);
}
