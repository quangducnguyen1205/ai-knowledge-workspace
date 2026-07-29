package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;
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

    void deleteByAssetId(UUID assetId);
}
