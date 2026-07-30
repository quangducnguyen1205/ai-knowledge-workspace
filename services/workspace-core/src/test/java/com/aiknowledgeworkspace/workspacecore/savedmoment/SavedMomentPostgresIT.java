package com.aiknowledgeworkspace.workspacecore.savedmoment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.command.SaveMomentCommand;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentTargetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in.SavedMomentUseCase;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentAlreadySavedException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentStore;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentView;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Saved-moment persistence, uniqueness and authorization against a real disposable PostgreSQL
 * server. The unique constraint is the concurrency boundary, so concurrent duplicate saves must
 * converge on one row instead of raising a primary-key failure to the caller.
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class SavedMomentPostgresIT {

    private static final String DATABASE = "workspace_core_saved_moments";
    private static final String USERNAME = "workspace_core";
    private static final String PASSWORD = "workspace_core";
    private static final String CURRENT_USER = "local-dev-user";
    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>(DockerImageName.parse("postgres:16.10-alpine"))
                    .withEnv("POSTGRES_DB", DATABASE)
                    .withEnv("POSTGRES_USER", USERNAME)
                    .withEnv("POSTGRES_PASSWORD", PASSWORD)
                    .withExposedPorts(5432);

    static {
        POSTGRES.start();
    }

    @Autowired
    private SavedMomentUseCase savedMoments;

    @Autowired
    private SavedMomentStore savedMomentStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private CanonicalTranscriptStore transcriptStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DATABASE);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void savingThroughTheUseCaseStoresCanonicalIdentityOnPostgres() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 7, 1000L, 4000L, "Canonical text.")));

        SavedMomentView view = savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));

        assertThat(view.workspaceId()).isEqualTo(fixture.workspaceId());
        assertThat(view.assetTitle()).isEqualTo("Lecture");
        assertThat(view.sourceType()).isEqualTo("UPLOAD");
        assertThat(view.segmentIndex()).isEqualTo(7);
        assertThat(view.startMs()).isEqualTo(1000L);
        assertThat(view.text()).isEqualTo("Canonical text.");
        assertThat(savedMomentCount(fixture.assetId())).isEqualTo(1);
    }

    @Test
    void repeatedSaveReturnsTheSameRecordWithoutASecondRow() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));

        SavedMomentView first = savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));
        SavedMomentView second = savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));
        SavedMomentView third = savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));

        assertThat(second.savedMomentId()).isEqualTo(first.savedMomentId());
        assertThat(third.savedMomentId()).isEqualTo(first.savedMomentId());
        assertThat(second.savedAt()).isEqualTo(first.savedAt());
        assertThat(savedMomentCount(fixture.assetId())).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateSavesConvergeOnOneRowWithoutSurfacingADatabaseFailure() throws Exception {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));
        int writers = 8;
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        AtomicInteger failures = new AtomicInteger();

        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int index = 0; index < writers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(30, TimeUnit.SECONDS);
                    try {
                        return savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"))
                                .savedMomentId();
                    } catch (RuntimeException exception) {
                        failures.incrementAndGet();
                        throw exception;
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<UUID> savedMomentIds = new ArrayList<>();
            for (Future<UUID> result : results) {
                savedMomentIds.add(result.get(60, TimeUnit.SECONDS));
            }

            assertThat(failures.get()).isZero();
            assertThat(savedMomentIds).hasSize(writers).containsOnly(savedMomentIds.getFirst());
            assertThat(savedMomentCount(fixture.assetId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void theDatabaseRejectsADuplicateEvenWhenTheApplicationIsBypassed() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));
        savedMomentStore.insert(new SavedMomentRecord(
                UUID.randomUUID(), CURRENT_USER, fixture.workspaceId(), fixture.assetId(), "row-1", Instant.now()
        ));

        assertThatThrownBy(() -> savedMomentStore.insert(new SavedMomentRecord(
                UUID.randomUUID(), CURRENT_USER, fixture.workspaceId(), fixture.assetId(), "row-1", Instant.now()
        ))).isInstanceOf(SavedMomentAlreadySavedException.class);
        assertThat(savedMomentCount(fixture.assetId())).isEqualTo(1);
    }

    @Test
    void savingAForeignAssetIsRejectedAndPersistsNothing() {
        Fixture foreign = persist("someone-else", List.of(row("row-1", 1, 0L, 1000L, "Private.")));

        assertThatThrownBy(() -> savedMoments.save(new SaveMomentCommand(foreign.assetId(), "row-1")))
                .isInstanceOf(SavedMomentTargetNotFoundException.class);
        assertThat(savedMomentCount(foreign.assetId())).isZero();
    }

    @Test
    void savingARowThatIsNotCanonicalIsRejectedAndPersistsNothing() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));

        assertThatThrownBy(() -> savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-unknown")))
                .isInstanceOf(SavedMomentTargetNotFoundException.class);
        assertThat(savedMomentCount(fixture.assetId())).isZero();
    }

    @Test
    void listIsScopedToTheWorkspaceAndOrderedNewestFirst() {
        Fixture first = persist(CURRENT_USER, List.of(
                row("row-1", 1, 0L, 1000L, "First."), row("row-2", 2, 1000L, 2000L, "Second.")
        ));
        Fixture other = persistInWorkspace(first.workspaceId(), List.of(row("row-3", 3, 2000L, 3000L, "Third.")));
        Fixture separateWorkspace = persist(CURRENT_USER, List.of(row("row-4", 4, 0L, 1000L, "Elsewhere.")));

        savedMoments.save(new SaveMomentCommand(first.assetId(), "row-1"));
        savedMoments.save(new SaveMomentCommand(first.assetId(), "row-2"));
        savedMoments.save(new SaveMomentCommand(other.assetId(), "row-3"));
        savedMoments.save(new SaveMomentCommand(separateWorkspace.assetId(), "row-4"));

        assertThat(savedMoments.listForWorkspace(first.workspaceId()).items())
                .extracting(SavedMomentView::transcriptRowId)
                .containsExactly("row-3", "row-2", "row-1");
        assertThat(savedMoments.listForWorkspace(separateWorkspace.workspaceId()).items())
                .extracting(SavedMomentView::transcriptRowId)
                .containsExactly("row-4");
    }

    @Test
    void aSavedMomentDisappearsFromTheListWhenItsCanonicalRowIsReplaced() {
        Fixture fixture = persist(CURRENT_USER, List.of(
                row("row-1", 1, 0L, 1000L, "Original."), row("row-2", 2, 1000L, 2000L, "Kept.")
        ));
        savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));
        savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-2"));

        replaceTranscript(fixture.assetId(), List.of(
                row("row-2", 2, 1000L, 2000L, "Kept."),
                row("row-3", 1, 0L, 1000L, "Replacement.")
        ));

        assertThat(savedMoments.listForWorkspace(fixture.workspaceId()).items())
                .extracting(SavedMomentView::transcriptRowId)
                .containsExactly("row-2");
        assertThat(savedMomentCount(fixture.assetId())).isEqualTo(2);
    }

    @Test
    void deletingAnAssetLeavesNoUsableOrphanSavedMoment() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));
        savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));

        jdbcTemplate.update("delete from assets where id = ?", fixture.assetId());

        assertThat(savedMomentCount(fixture.assetId())).isZero();
        assertThat(savedMoments.listForWorkspace(fixture.workspaceId()).items()).isEmpty();
    }

    @Test
    void removingRequiresOwnershipOfTheSavedMoment() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));
        SavedMomentView saved = savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-1"));
        UUID foreignSavedMomentId = savedMomentStore.insert(new SavedMomentRecord(
                UUID.randomUUID(), "another-user", fixture.workspaceId(), fixture.assetId(), "row-1", Instant.now()
        )).savedMomentId();

        assertThatThrownBy(() -> savedMoments.remove(foreignSavedMomentId))
                .isInstanceOf(SavedMomentNotFoundException.class);
        assertThatThrownBy(() -> savedMoments.remove(UUID.randomUUID()))
                .isInstanceOf(SavedMomentNotFoundException.class);

        savedMoments.remove(saved.savedMomentId());

        assertThat(savedMoments.listForWorkspace(fixture.workspaceId()).items()).isEmpty();
        assertThat(savedMomentCount(fixture.assetId())).isEqualTo(1);
    }

    @Test
    void anotherUsersSavedMomentNeverAppearsInTheCurrentUsersList() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-1", 1, 0L, 1000L, "Text.")));
        savedMomentStore.insert(new SavedMomentRecord(
                UUID.randomUUID(), "another-user", fixture.workspaceId(), fixture.assetId(), "row-1", Instant.now()
        ));

        assertThat(savedMoments.listForWorkspace(fixture.workspaceId()).items()).isEmpty();
    }

    @Test
    void theListIsBoundedByTheServerOwnedMaximum() {
        Fixture fixture = persist(CURRENT_USER, List.of(row("row-0", 0, 0L, 1000L, "Row 0.")));
        List<AssetTranscriptRowInput> rows = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            rows.add(row("row-" + index, index, (long) index * 1000, (long) index * 1000 + 500, "Row " + index));
        }
        replaceTranscript(fixture.assetId(), rows);
        for (int index = 0; index < 105; index++) {
            savedMoments.save(new SaveMomentCommand(fixture.assetId(), "row-" + index));
        }

        assertThat(savedMomentCount(fixture.assetId())).isEqualTo(105);
        assertThat(savedMoments.listForWorkspace(fixture.workspaceId()).items()).hasSize(100);
        assertThat(savedMoments.listForWorkspace(fixture.workspaceId()).maxItems()).isEqualTo(100);
    }

    private void replaceTranscript(UUID assetId, List<AssetTranscriptRowInput> rows) {
        transactionTemplate.executeWithoutResult(status -> transcriptStore.replace(assetId, rows));
    }

    private int savedMomentCount(UUID assetId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from saved_moments where asset_id = ?", Integer.class, assetId
        );
        return count == null ? 0 : count;
    }

    private AssetTranscriptRowInput row(String id, int segmentIndex, Long startMs, Long endMs, String text) {
        return new AssetTranscriptRowInput(
                id, "video-1", segmentIndex, startMs, endMs, text, "2026-07-30T00:00:00Z"
        );
    }

    private Fixture persist(String ownerId, List<AssetTranscriptRowInput> rows) {
        UUID workspaceId = workspaceStore
                .save(new Workspace(UUID.randomUUID(), "Saved moments", ownerId, false)).getId();
        return new Fixture(workspaceId, persistAsset(workspaceId, rows));
    }

    private Fixture persistInWorkspace(UUID workspaceId, List<AssetTranscriptRowInput> rows) {
        return new Fixture(workspaceId, persistAsset(workspaceId, rows));
    }

    private UUID persistAsset(UUID workspaceId, List<AssetTranscriptRowInput> rows) {
        UUID assetId = UUID.randomUUID();
        assetStore.save(Asset.uploaded(
                assetId, "lecture.mp4", "Lecture", AssetStatus.SEARCHABLE, workspaceId,
                "workspace-media", "objects/" + assetId + ".mp4", "video/mp4", 42L, "etag-1"
        ));
        replaceTranscript(assetId, rows);
        return assetId;
    }

    private record Fixture(UUID workspaceId, UUID assetId) {
    }
}
