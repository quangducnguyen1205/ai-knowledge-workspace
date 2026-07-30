package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.ResumableAssetPlayback;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Continue-watching eligibility, ordering, bounded reads and the current-Asset projection against
 * the Flyway-migrated schema.
 *
 * <p>The production write is the PostgreSQL {@code INSERT ... ON CONFLICT DO UPDATE} statement that
 * H2 cannot parse, so rows are seeded here with portable SQL exactly as
 * {@code AssetPlaybackProgressPersistenceTest} does. Transitions that depend on that upsert —
 * resetting to zero and clearing completion — are proven against real PostgreSQL in
 * {@code AssetPlaybackProgressConcurrencyPostgresTest}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-continue-watching;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class ContinueWatchingPersistenceTest {

    private static final Instant BASE = Instant.parse("2026-07-30T08:00:00Z");
    private static final String USER = "user-1";

    @Autowired
    private AssetPlaybackProgressStore progressStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void startedIncompleteProgressAppearsWithCurrentAssetPresentationData() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Vector Clocks Lecture");
        seedProgress(assetId, USER, 61_000, false, BASE);

        assertThat(progressStore.findResumable(USER, workspaceId, 12)).singleElement().satisfies(item -> {
            assertThat(item.assetId()).isEqualTo(assetId);
            assertThat(item.workspaceId()).isEqualTo(workspaceId);
            assertThat(item.assetTitle()).isEqualTo("Vector Clocks Lecture");
            assertThat(item.sourceType()).isEqualTo(AssetSourceType.UPLOAD);
            assertThat(item.positionMs()).isEqualTo(61_000);
            assertThat(item.completed()).isFalse();
            assertThat(item.updatedAt()).isEqualTo(BASE);
        });
    }

    @Test
    void zeroProgressIsExcluded() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Never started");
        seedProgress(assetId, USER, 0, false, BASE);

        assertThat(progressStore.findResumable(USER, workspaceId, 12)).isEmpty();
    }

    @Test
    void completedProgressIsExcluded() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Finished");
        seedProgress(assetId, USER, 90_000, true, BASE);

        assertThat(progressStore.findResumable(USER, workspaceId, 12)).isEmpty();
    }

    @Test
    void resettingToZeroRemovesAnAssetAndReopeningItRestoresIt() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Restartable");
        seedProgress(assetId, USER, 61_000, false, BASE);
        assertThat(progressStore.findResumable(USER, workspaceId, 12)).hasSize(1);

        seedProgress(assetId, USER, 0, false, BASE.plusSeconds(10));
        assertThat(progressStore.findResumable(USER, workspaceId, 12)).isEmpty();

        seedProgress(assetId, USER, 5_000, false, BASE.plusSeconds(20));
        assertThat(progressStore.findResumable(USER, workspaceId, 12)).hasSize(1);
    }

    @Test
    void clearingCompletionWithAPositivePositionMakesTheAssetAppearAgain() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Replayable");
        seedProgress(assetId, USER, 90_000, true, BASE);
        assertThat(progressStore.findResumable(USER, workspaceId, 12)).isEmpty();

        seedProgress(assetId, USER, 90_000, false, BASE.plusSeconds(30));

        assertThat(progressStore.findResumable(USER, workspaceId, 12)).hasSize(1);
    }

    @Test
    void anotherUsersProgressNeverAppears() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Shared asset");
        seedProgress(assetId, USER, 61_000, false, BASE);
        seedProgress(assetId, "user-2", 30_000, false, BASE.plusSeconds(60));

        assertThat(progressStore.findResumable(USER, workspaceId, 12))
                .singleElement()
                .satisfies(item -> assertThat(item.positionMs()).isEqualTo(61_000));
        assertThat(progressStore.findResumable("user-2", workspaceId, 12))
                .singleElement()
                .satisfies(item -> assertThat(item.positionMs()).isEqualTo(30_000));
        assertThat(progressStore.findResumable("user-3", workspaceId, 12)).isEmpty();
    }

    @Test
    void anotherWorkspaceNeverLeaksIntoTheList() {
        UUID firstWorkspace = workspace("owner-1");
        UUID secondWorkspace = workspace("owner-1");
        UUID firstAsset = uploadAsset(firstWorkspace, "First workspace asset");
        UUID secondAsset = uploadAsset(secondWorkspace, "Second workspace asset");
        seedProgress(firstAsset, USER, 10_000, false, BASE);
        seedProgress(secondAsset, USER, 20_000, false, BASE.plusSeconds(60));

        assertThat(progressStore.findResumable(USER, firstWorkspace, 12))
                .singleElement()
                .satisfies(item -> assertThat(item.assetTitle()).isEqualTo("First workspace asset"));
        assertThat(progressStore.findResumable(USER, secondWorkspace, 12))
                .singleElement()
                .satisfies(item -> assertThat(item.assetTitle()).isEqualTo("Second workspace asset"));
    }

    @Test
    void aDeletedAssetDisappearsFromTheList() {
        UUID workspaceId = workspace("owner-1");
        UUID removedAsset = uploadAsset(workspaceId, "Removed");
        UUID keptAsset = uploadAsset(workspaceId, "Kept");
        seedProgress(removedAsset, USER, 10_000, false, BASE);
        seedProgress(keptAsset, USER, 20_000, false, BASE.minusSeconds(60));
        assertThat(progressStore.findResumable(USER, workspaceId, 12)).hasSize(2);

        entityManager.flush();
        jdbcTemplate.update("delete from assets where id = ?", removedAsset);

        assertThat(progressStore.findResumable(USER, workspaceId, 12))
                .singleElement()
                .satisfies(item -> assertThat(item.assetTitle()).isEqualTo("Kept"));
    }

    @Test
    void theListIsNewestFirstWithAssetIdAsADeterministicTieBreak() {
        UUID workspaceId = workspace("owner-1");
        UUID newest = uploadAsset(workspaceId, "Newest");
        UUID tieLow = fixedUploadAsset(workspaceId, "Tie low",
                UUID.fromString("00000000-0000-4000-8000-000000000001"));
        UUID tieHigh = fixedUploadAsset(workspaceId, "Tie high",
                UUID.fromString("00000000-0000-4000-8000-000000000002"));
        UUID oldest = uploadAsset(workspaceId, "Oldest");
        seedProgress(newest, USER, 1_000, false, BASE.plusSeconds(120));
        seedProgress(tieHigh, USER, 1_000, false, BASE.plusSeconds(60));
        seedProgress(tieLow, USER, 1_000, false, BASE.plusSeconds(60));
        seedProgress(oldest, USER, 1_000, false, BASE);

        assertThat(progressStore.findResumable(USER, workspaceId, 12))
                .extracting(ResumableAssetPlayback::assetTitle)
                .containsExactly("Newest", "Tie low", "Tie high", "Oldest");
    }

    @Test
    void theReadIsBoundedByTheRequestedMaximum() {
        UUID workspaceId = workspace("owner-1");
        for (int index = 0; index < 15; index++) {
            UUID assetId = uploadAsset(workspaceId, "Asset " + index);
            seedProgress(assetId, USER, 1_000L * (index + 1), false, BASE.plusSeconds(index));
        }

        assertThat(progressStore.findResumable(USER, workspaceId, 12)).hasSize(12);
        assertThat(progressStore.findResumable(USER, workspaceId, 12))
                .extracting(ResumableAssetPlayback::assetTitle)
                .startsWith("Asset 14", "Asset 13", "Asset 12");
    }

    @Test
    void aRenamedAssetIsProjectedFromCurrentStateRatherThanASnapshot() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Original title");
        seedProgress(assetId, USER, 61_000, false, BASE);
        assertThat(progressStore.findResumable(USER, workspaceId, 12).getFirst().assetTitle())
                .isEqualTo("Original title");

        entityManager.flush();
        jdbcTemplate.update("update assets set title = ? where id = ?", "Renamed title", assetId);
        entityManager.clear();

        assertThat(progressStore.findResumable(USER, workspaceId, 12).getFirst().assetTitle())
                .isEqualTo("Renamed title");
    }

    @Test
    void bothSourceTypesAreListedWithTheirCurrentSource() {
        UUID workspaceId = workspace("owner-1");
        UUID upload = uploadAsset(workspaceId, "Uploaded");
        UUID youtube = youtubeAsset(workspaceId, "From YouTube");
        seedProgress(upload, USER, 10_000, false, BASE);
        seedProgress(youtube, USER, 20_000, false, BASE.plusSeconds(60));

        assertThat(progressStore.findResumable(USER, workspaceId, 12))
                .extracting(ResumableAssetPlayback::sourceType)
                .containsExactly(AssetSourceType.YOUTUBE, AssetSourceType.UPLOAD);
    }

    @Test
    void readingTheListNeverChangesStoredProgress() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Untouched");
        seedProgress(assetId, USER, 61_000, false, BASE);

        progressStore.findResumable(USER, workspaceId, 12);
        progressStore.findResumable(USER, workspaceId, 12);

        assertThat(progressStore.find(assetId, USER)).get().satisfies(snapshot -> {
            assertThat(snapshot.positionMs()).isEqualTo(61_000);
            assertThat(snapshot.completed()).isFalse();
            assertThat(snapshot.updatedAt()).isEqualTo(BASE);
        });
        assertThat(progressRowCount()).isEqualTo(1);
    }

    @Test
    void invalidArgumentsResolveToAnEmptyListRatherThanAnUnboundedRead() {
        UUID workspaceId = workspace("owner-1");
        UUID assetId = uploadAsset(workspaceId, "Asset");
        seedProgress(assetId, USER, 61_000, false, BASE);

        assertThat(progressStore.findResumable(null, workspaceId, 12)).isEmpty();
        assertThat(progressStore.findResumable(USER, null, 12)).isEmpty();
        assertThat(progressStore.findResumable(USER, workspaceId, 0)).isEmpty();
        assertThat(progressStore.findResumable(USER, workspaceId, -1)).isEmpty();
    }

    /** Portable seed: the production upsert is PostgreSQL-only. */
    private void seedProgress(UUID assetId, String userId, long positionMs, boolean completed, Instant updatedAt) {
        // The Asset is written through JPA, so it must reach the database before the foreign key
        // of this plain-SQL insert is checked.
        entityManager.flush();
        jdbcTemplate.update(
                "merge into asset_playback_progress (asset_id, user_id, position_ms, completed, updated_at)"
                        + " key (asset_id, user_id) values (?, ?, ?, ?, ?)",
                assetId, userId, positionMs, completed, java.sql.Timestamp.from(updatedAt)
        );
    }

    private int progressRowCount() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from asset_playback_progress", Integer.class);
        return count == null ? 0 : count;
    }

    private UUID workspace(String ownerId) {
        return workspaceStore.save(new Workspace(UUID.randomUUID(), "Continue watching", ownerId, false)).getId();
    }

    private UUID uploadAsset(UUID workspaceId, String title) {
        return fixedUploadAsset(workspaceId, title, UUID.randomUUID());
    }

    private UUID fixedUploadAsset(UUID workspaceId, String title, UUID assetId) {
        return assetStore.save(Asset.uploaded(
                assetId, "lecture.mp4", title, AssetStatus.SEARCHABLE, workspaceId,
                "workspace-media", "objects/" + assetId + ".mp4", "video/mp4", 42L, "etag-1"
        )).getId();
    }

    private UUID youtubeAsset(UUID workspaceId, String title) {
        UUID assetId = UUID.randomUUID();
        return assetStore.saveYoutube(Asset.youtube(
                assetId, "abc_DEF-" + Math.abs(assetId.hashCode() % 1000), title,
                AssetStatus.SEARCHABLE, workspaceId
        )).getId();
    }

    @Test
    void progressForAnAssetInEveryStatusRemainsListableBecauseProgressIsStatusIndependent() {
        UUID workspaceId = workspace("owner-1");
        List<AssetStatus> statuses = List.of(
                AssetStatus.PROCESSING, AssetStatus.TRANSCRIPT_READY, AssetStatus.SEARCHABLE, AssetStatus.FAILED);
        for (AssetStatus status : statuses) {
            UUID assetId = UUID.randomUUID();
            assetStore.save(Asset.uploaded(
                    assetId, "lecture.mp4", "Asset " + status, status, workspaceId,
                    "workspace-media", "objects/" + assetId + ".mp4", "video/mp4", 42L, "etag-1"));
            seedProgress(assetId, USER, 10_000, false, BASE);
        }

        assertThat(progressStore.findResumable(USER, workspaceId, 12)).hasSize(statuses.size());
    }
}
