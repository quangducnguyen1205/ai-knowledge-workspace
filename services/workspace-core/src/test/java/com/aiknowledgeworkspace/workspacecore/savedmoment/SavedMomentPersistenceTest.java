package com.aiknowledgeworkspace.workspacecore.savedmoment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentAlreadySavedException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentStore;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved-moment mapping, uniqueness, ordering, bounded reads and Asset-scoped cleanup against the
 * Flyway-migrated schema with {@code ddl-auto=validate}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-saved-moments;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class SavedMomentPersistenceTest {

    private static final Instant BASE = Instant.parse("2026-07-30T08:00:00Z");

    @Autowired
    private SavedMomentStore savedMomentStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aSavedMomentRoundTripsEveryPersistedField() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        SavedMomentRecord record = record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", BASE);

        SavedMomentRecord stored = savedMomentStore.insert(record);

        assertThat(stored).isEqualTo(record);
        assertThat(savedMomentStore.find("user-1", assetId, "row-1")).contains(record);
    }

    @Test
    void theSameRowSavedTwiceByOneUserIsRejectedByTheDatabase() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", BASE));

        assertThatThrownBy(() -> savedMomentStore.insert(
                record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", BASE.plusSeconds(60))
        )).isInstanceOf(SavedMomentAlreadySavedException.class);
    }

    @Test
    void differentUsersSaveTheSameCanonicalRowIndependently() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);

        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-2", workspaceId, assetId, "row-1", BASE));

        assertThat(savedMomentStore.find("user-1", assetId, "row-1")).isPresent();
        assertThat(savedMomentStore.find("user-2", assetId, "row-1")).isPresent();
        assertThat(savedMomentStore.findRecent("user-1", workspaceId, 100)).hasSize(1);
        assertThat(savedMomentStore.findRecent("user-2", workspaceId, 100)).hasSize(1);
    }

    @Test
    void oneUserSavesDifferentRowsOfTheSameAsset() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);

        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-2", BASE));

        assertThat(savedMomentStore.findRecent("user-1", workspaceId, 100)).hasSize(2);
    }

    @Test
    void readsAreScopedToTheOwningUserAndWorkspace() {
        UUID firstWorkspace = persistWorkspace("owner-1");
        UUID secondWorkspace = persistWorkspace("owner-1");
        UUID firstAsset = persistUploadAsset(firstWorkspace);
        UUID secondAsset = persistUploadAsset(secondWorkspace);
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", firstWorkspace, firstAsset, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", secondWorkspace, secondAsset, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-2", firstWorkspace, firstAsset, "row-9", BASE));

        assertThat(savedMomentStore.findRecent("user-1", firstWorkspace, 100))
                .singleElement()
                .satisfies(found -> assertThat(found.assetId()).isEqualTo(firstAsset));
        assertThat(savedMomentStore.findRecent("user-1", secondWorkspace, 100))
                .singleElement()
                .satisfies(found -> assertThat(found.assetId()).isEqualTo(secondAsset));
        assertThat(savedMomentStore.findRecent("user-3", firstWorkspace, 100)).isEmpty();
    }

    @Test
    void listsAreNewestFirstWithADeterministicTieBreak() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "old", BASE));
        savedMomentStore.insert(record(lowerId, "user-1", workspaceId, assetId, "tie-low", BASE.plusSeconds(60)));
        savedMomentStore.insert(record(higherId, "user-1", workspaceId, assetId, "tie-high", BASE.plusSeconds(60)));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "new", BASE.plusSeconds(120)));

        assertThat(savedMomentStore.findRecent("user-1", workspaceId, 100))
                .extracting(SavedMomentRecord::transcriptRowId)
                .containsExactly("new", "tie-high", "tie-low", "old");
    }

    @Test
    void readsAreBoundedByTheRequestedServerOwnedMaximum() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        for (int index = 0; index < 12; index++) {
            savedMomentStore.insert(record(
                    UUID.randomUUID(), "user-1", workspaceId, assetId, "row-" + index,
                    BASE.plusSeconds(index)
            ));
        }

        assertThat(savedMomentStore.findRecent("user-1", workspaceId, 5))
                .hasSize(5)
                .extracting(SavedMomentRecord::transcriptRowId)
                .containsExactly("row-11", "row-10", "row-9", "row-8", "row-7");
    }

    @Test
    void ownedLookupNeverReturnsAnotherUsersRecord() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        UUID savedMomentId = UUID.randomUUID();
        savedMomentStore.insert(record(savedMomentId, "user-1", workspaceId, assetId, "row-1", BASE));

        assertThat(savedMomentStore.findOwned(savedMomentId, "user-1")).isPresent();
        assertThat(savedMomentStore.findOwned(savedMomentId, "user-2")).isEmpty();
        assertThat(savedMomentStore.findOwned(UUID.randomUUID(), "user-1")).isEmpty();
    }

    @Test
    void removingOneSavedMomentLeavesTheOthersIntact() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        UUID removedId = UUID.randomUUID();
        savedMomentStore.insert(record(removedId, "user-1", workspaceId, assetId, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-2", BASE));

        savedMomentStore.delete(removedId);

        assertThat(savedMomentStore.findRecent("user-1", workspaceId, 100))
                .extracting(SavedMomentRecord::transcriptRowId)
                .containsExactly("row-2");
    }

    @Test
    void assetScopedCleanupRemovesEverySavedMomentOfThatAssetOnly() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID removedAsset = persistUploadAsset(workspaceId);
        UUID retainedAsset = persistUploadAsset(workspaceId);
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, removedAsset, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-2", workspaceId, removedAsset, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, retainedAsset, "row-1", BASE));

        savedMomentStore.deleteForAsset(removedAsset);

        assertThat(savedMomentCount(removedAsset)).isZero();
        assertThat(savedMomentCount(retainedAsset)).isEqualTo(1);
    }

    @Test
    void deletingAnAssetCascadesItsSavedMomentsAsDefenceInDepth() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        UUID otherAssetId = persistUploadAsset(workspaceId);
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", BASE));
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, otherAssetId, "row-1", BASE));

        jdbcTemplate.update("delete from assets where id = ?", assetId);

        assertThat(savedMomentCount(assetId)).isZero();
        assertThat(savedMomentCount(otherAssetId)).isEqualTo(1);
    }

    @Test
    void aSavedMomentCannotReferenceAnAssetThatDoesNotExist() {
        UUID workspaceId = persistWorkspace("owner-1");

        assertThatThrownBy(() -> savedMomentStore.insert(
                record(UUID.randomUUID(), "user-1", workspaceId, UUID.randomUUID(), "row-1", BASE)
        )).isInstanceOf(SavedMomentAlreadySavedException.class);
    }

    @Test
    void savedAtIsStoredWithTimezoneAwarePrecision() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        Instant savedAt = BASE.plusMillis(456);
        savedMomentStore.insert(record(UUID.randomUUID(), "user-1", workspaceId, assetId, "row-1", savedAt));

        assertThat(savedMomentStore.find("user-1", assetId, "row-1"))
                .get()
                .satisfies(found -> assertThat(found.savedAt().truncatedTo(ChronoUnit.MILLIS))
                        .isEqualTo(savedAt.truncatedTo(ChronoUnit.MILLIS)));
    }

    private int savedMomentCount(UUID assetId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from saved_moments where asset_id = ?", Integer.class, assetId
        );
        return count == null ? 0 : count;
    }

    private SavedMomentRecord record(
            UUID savedMomentId, String userId, UUID workspaceId, UUID assetId, String rowId, Instant savedAt
    ) {
        return new SavedMomentRecord(savedMomentId, userId, workspaceId, assetId, rowId, savedAt);
    }

    private UUID persistWorkspace(String ownerId) {
        return workspaceStore.save(new Workspace(UUID.randomUUID(), "Saved moments", ownerId, false)).getId();
    }

    private UUID persistUploadAsset(UUID workspaceId) {
        UUID assetId = UUID.randomUUID();
        return assetStore.save(Asset.uploaded(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.SEARCHABLE,
                workspaceId,
                "workspace-media",
                "objects/" + assetId + ".mp4",
                "video/mp4",
                42L,
                "etag-1"
        )).getId();
    }

    @Test
    void everyPersistedColumnIsMappedByTheEntity() {
        UUID workspaceId = persistWorkspace("owner-1");
        UUID assetId = persistUploadAsset(workspaceId);
        UUID savedMomentId = UUID.randomUUID();
        savedMomentStore.insert(record(savedMomentId, "user-1", workspaceId, assetId, "row-1", BASE));

        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where lower(table_name) = 'saved_moments'",
                String.class
        ).stream().map(column -> column.toLowerCase(java.util.Locale.ROOT)).toList();

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "user_id", "workspace_id", "asset_id", "transcript_row_id", "saved_at"
        );
    }
}
