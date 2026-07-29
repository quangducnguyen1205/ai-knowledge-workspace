package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-playback-progress;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class AssetPlaybackProgressPersistenceTest {

    private static final Instant FIRST_WRITE = Instant.parse("2026-07-29T08:00:00Z");
    private static final Instant SECOND_WRITE = Instant.parse("2026-07-29T08:05:30Z");

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private AssetPlaybackProgressStore progressStore;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        workspaceId = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "Playback progress", "owner-1", false
        )).getId();
    }

    @Test
    void upsertCreatesThenReplacesOneRowPerUserAndAsset() {
        UUID assetId = persistUpload();

        AssetPlaybackProgressSnapshot created = progressStore.upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);
        flush();

        assertThat(created).isEqualTo(new AssetPlaybackProgressSnapshot(12345L, false, FIRST_WRITE));
        assertThat(rowCount(assetId)).isEqualTo(1);

        AssetPlaybackProgressSnapshot replaced = progressStore.upsert(assetId, "user-1", 250L, true, SECOND_WRITE);
        flush();

        assertThat(replaced).isEqualTo(new AssetPlaybackProgressSnapshot(250L, true, SECOND_WRITE));
        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(progressStore.find(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(250L, true, SECOND_WRITE));
    }

    @Test
    void repeatingAnIdenticalWriteKeepsExactlyOneRow() {
        UUID assetId = persistUpload();

        progressStore.upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);
        progressStore.upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);
        flush();

        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(progressStore.find(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(12345L, false, FIRST_WRITE));
    }

    @Test
    void compositeIdentityKeepsUsersAndAssetsIsolated() {
        UUID firstAsset = persistUpload();
        UUID secondAsset = persistYoutube();

        progressStore.upsert(firstAsset, "user-1", 100L, false, FIRST_WRITE);
        progressStore.upsert(firstAsset, "user-2", 200L, true, FIRST_WRITE);
        progressStore.upsert(secondAsset, "user-1", 300L, false, FIRST_WRITE);
        flush();

        assertThat(progressStore.find(firstAsset, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(100L, false, FIRST_WRITE));
        assertThat(progressStore.find(firstAsset, "user-2"))
                .contains(new AssetPlaybackProgressSnapshot(200L, true, FIRST_WRITE));
        assertThat(progressStore.find(secondAsset, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(300L, false, FIRST_WRITE));
        assertThat(progressStore.find(secondAsset, "user-2")).isEmpty();
        assertThat(rowCount(firstAsset)).isEqualTo(2);
    }

    @Test
    void aLaterWriteRefreshesTheStoredTimestamp() {
        UUID assetId = persistUpload();

        progressStore.upsert(assetId, "user-1", 10L, false, FIRST_WRITE);
        flush();
        progressStore.upsert(assetId, "user-1", 20L, false, SECOND_WRITE);
        flush();

        assertThat(progressStore.find(assetId, "user-1"))
                .get()
                .extracting(AssetPlaybackProgressSnapshot::updatedAt)
                .isEqualTo(SECOND_WRITE);
    }

    @Test
    void youtubeAssetsPersistProgressWithoutAnyUploadObjectMetadata() {
        UUID assetId = persistYoutube();

        progressStore.upsert(assetId, "user-1", 4200L, false, FIRST_WRITE);
        flush();

        assertThat(progressStore.find(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(4200L, false, FIRST_WRITE));
    }

    @Test
    void theDatabaseRejectsANegativePositionAsDefenceInDepth() {
        UUID assetId = persistUpload();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into asset_playback_progress (asset_id, user_id, position_ms, completed, updated_at) "
                        + "values (?, ?, ?, false, current_timestamp)",
                assetId, "user-1", -1L
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void progressCannotReferenceAnAssetThatDoesNotExist() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into asset_playback_progress (asset_id, user_id, position_ms, completed, updated_at) "
                        + "values (?, ?, ?, false, current_timestamp)",
                UUID.randomUUID(), "user-1", 10L
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheAssetRowCascadesProgressAway() {
        UUID assetId = persistUpload();
        progressStore.upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);
        progressStore.upsert(assetId, "user-2", 6789L, true, FIRST_WRITE);
        flush();
        assertThat(rowCount(assetId)).isEqualTo(2);

        jdbcTemplate.update("delete from assets where id = ?", assetId);

        assertThat(rowCount(assetId)).isZero();
    }

    @Test
    void theStoreCanRemoveEveryProgressRowForOneAssetThroughTheDeletionBoundary() {
        UUID assetId = persistUpload();
        UUID otherAssetId = persistYoutube();
        progressStore.upsert(assetId, "user-1", 1L, false, FIRST_WRITE);
        progressStore.upsert(assetId, "user-2", 2L, false, FIRST_WRITE);
        progressStore.upsert(otherAssetId, "user-1", 3L, false, FIRST_WRITE);
        flush();

        progressStore.deleteForAsset(assetId);
        flush();

        assertThat(rowCount(assetId)).isZero();
        assertThat(rowCount(otherAssetId)).isEqualTo(1);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private int rowCount(UUID assetId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from asset_playback_progress where asset_id = ?",
                Integer.class,
                assetId
        );
        return count == null ? 0 : count;
    }

    private UUID persistUpload() {
        UUID assetId = UUID.randomUUID();
        assetStore.save(Asset.uploaded(
                assetId,
                "lecture.mp4",
                "Uploaded lecture",
                AssetStatus.PROCESSING,
                workspaceId,
                "workspace-media",
                "objects/" + assetId + ".mp4",
                "video/mp4",
                42L,
                "etag-1"
        ));
        flush();
        return assetId;
    }

    private UUID persistYoutube() {
        UUID assetId = UUID.randomUUID();
        assetStore.saveYoutube(Asset.youtube(
                assetId,
                "vid" + assetId.toString().replace("-", "").substring(0, 8),
                "YouTube video",
                AssetStatus.SEARCHABLE,
                workspaceId
        ));
        flush();
        return assetId;
    }
}
