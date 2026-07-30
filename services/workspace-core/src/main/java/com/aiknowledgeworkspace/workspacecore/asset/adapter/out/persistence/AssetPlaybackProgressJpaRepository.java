package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetPlaybackProgressJpaRepository
        extends JpaRepository<AssetPlaybackProgressEntry, AssetPlaybackProgressEntryId> {

    /**
     * One atomic PostgreSQL statement. The composite primary key is the conflict target, so two
     * concurrent first writes for the same user and Asset both succeed: the loser of the insert
     * race is turned into an update by the database instead of surfacing a primary-key violation.
     * There is no read-then-branch, no retry loop, no lock and no version column; the write that
     * the database applies last determines the stored position, completion flag and timestamp.
     */
    String UPSERT_SQL = """
            INSERT INTO asset_playback_progress (
                asset_id,
                user_id,
                position_ms,
                completed,
                updated_at
            )
            VALUES (:assetId, :userId, :positionMs, :completed, :updatedAt)
            ON CONFLICT (asset_id, user_id)
            DO UPDATE SET
                position_ms = EXCLUDED.position_ms,
                completed = EXCLUDED.completed,
                updated_at = EXCLUDED.updated_at
            """;

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = UPSERT_SQL, nativeQuery = true)
    void upsert(
            @Param("assetId") UUID assetId,
            @Param("userId") String userId,
            @Param("positionMs") long positionMs,
            @Param("completed") boolean completed,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Resumable progress joined to the Asset that still owns it, so the projection always carries
     * current title and source rather than a stored presentation snapshot. Progress rows whose
     * Asset was deleted or whose Asset lives in another Workspace simply do not join.
     *
     * <p>{@code updated_at} is non-null in the schema; the predicate stays explicit so the
     * eligibility rule is readable in one place and survives a future nullable column.
     */
    @Query("""
            select new com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence.ResumableAssetPlaybackRow(
                asset.id, asset.workspaceId, asset.title, asset.sourceType,
                entry.positionMs, entry.completed, entry.updatedAt)
            from AssetPlaybackProgressEntry entry
            join Asset asset on asset.id = entry.assetId
            where entry.userId = :userId
              and asset.workspaceId = :workspaceId
              and entry.positionMs > 0
              and entry.completed = false
              and entry.updatedAt is not null
            order by entry.updatedAt desc, asset.id asc
            """)
    List<ResumableAssetPlaybackRow> findResumable(
            @Param("userId") String userId,
            @Param("workspaceId") UUID workspaceId,
            Pageable pageable
    );

    void deleteByAssetId(UUID assetId);
}
