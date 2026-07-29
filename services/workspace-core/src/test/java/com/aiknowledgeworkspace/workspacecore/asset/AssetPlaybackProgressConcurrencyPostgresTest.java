package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the production atomic upsert on a real PostgreSQL server.
 *
 * <p>H2 cannot parse {@code INSERT ... ON CONFLICT ... DO UPDATE}, so the production statement is
 * validated here instead of being weakened to fit the default test profile. The test is skipped
 * unless a PostgreSQL server is offered through the environment, which keeps
 * {@code mvn test} portable:
 *
 * <pre>
 * WORKSPACE_CORE_IT_POSTGRES_URL=jdbc:postgresql://localhost:5434/postgres
 * WORKSPACE_CORE_IT_POSTGRES_USER=workspace_core
 * WORKSPACE_CORE_IT_POSTGRES_PASSWORD=workspace_core
 * </pre>
 *
 * <p>It never touches product data: a throwaway database is created for the class, migrated by
 * Flyway, and dropped afterwards.
 */
@EnabledIfEnvironmentVariable(named = "WORKSPACE_CORE_IT_POSTGRES_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class AssetPlaybackProgressConcurrencyPostgresTest {

    private static final Instant FIRST_WRITE = Instant.parse("2026-07-29T08:00:00Z");
    private static final Instant SECOND_WRITE = Instant.parse("2026-07-29T08:05:30Z");
    private static final long AWAIT_SECONDS = 20L;

    private static String adminUrl;
    private static String username;
    private static String password;
    private static String throwawayDatabase;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private AssetPlaybackProgressStore progressStore;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID workspaceId;

    @DynamicPropertySource
    static void throwawayPostgresDatabase(DynamicPropertyRegistry registry) throws Exception {
        adminUrl = System.getenv("WORKSPACE_CORE_IT_POSTGRES_URL");
        username = System.getenv("WORKSPACE_CORE_IT_POSTGRES_USER");
        password = System.getenv("WORKSPACE_CORE_IT_POSTGRES_PASSWORD");
        throwawayDatabase = "workspace_core_playback_it_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + throwawayDatabase);
        }

        String testUrl = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + throwawayDatabase;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    @AfterAll
    static void dropThrowawayDatabase() throws Exception {
        if (throwawayDatabase == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                            + throwawayDatabase + "' AND pid <> pg_backend_pid()"
            );
            statement.execute("DROP DATABASE IF EXISTS " + throwawayDatabase);
        }
    }

    @BeforeEach
    void setUp() {
        workspaceId = transactionTemplate.execute(status -> workspaceStore.save(new Workspace(
                UUID.randomUUID(), "Playback concurrency", "owner-1", false
        )).getId());
    }

    // ------------------------------------------------------- upsert behavior

    @Test
    void theAtomicStatementCreatesThenReplacesExactlyOneRow() {
        UUID assetId = persistUpload();

        AssetPlaybackProgressSnapshot created = upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);
        assertThat(created).isEqualTo(new AssetPlaybackProgressSnapshot(12345L, false, FIRST_WRITE));
        assertThat(rowCount(assetId)).isEqualTo(1);

        AssetPlaybackProgressSnapshot replaced = upsert(assetId, "user-1", 250L, true, SECOND_WRITE);
        assertThat(replaced).isEqualTo(new AssetPlaybackProgressSnapshot(250L, true, SECOND_WRITE));
        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(250L, true, SECOND_WRITE));
    }

    @Test
    void zeroAndCompletionAndTimestampAllRoundTrip() {
        UUID assetId = persistUpload();

        upsert(assetId, "user-1", 0L, false, FIRST_WRITE);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(0L, false, FIRST_WRITE));

        upsert(assetId, "user-1", 53480L, true, SECOND_WRITE);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(53480L, true, SECOND_WRITE));
    }

    @Test
    void repeatingAnIdenticalWriteKeepsExactlyOneRow() {
        UUID assetId = persistUpload();

        upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);
        upsert(assetId, "user-1", 12345L, false, FIRST_WRITE);

        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(12345L, false, FIRST_WRITE));
    }

    @Test
    void youtubeAssetsUseTheSameAtomicStatement() {
        UUID assetId = persistYoutube();

        upsert(assetId, "user-1", 4200L, false, FIRST_WRITE);

        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(4200L, false, FIRST_WRITE));
    }

    // -------------------------------------------------- concurrent first write

    @Test
    void twoConcurrentFirstWritesBothSucceedAndTheLastCommittedWriteWins() throws Exception {
        UUID assetId = persistUpload();

        ConcurrentOutcome outcome = runInterleavedWrites(
                assetId, "user-1",
                new Write(1000L, false, FIRST_WRITE),
                new Write(250L, true, SECOND_WRITE)
        );

        assertThat(outcome.failures()).isEmpty();
        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(250L, true, SECOND_WRITE));
    }

    @Test
    void manyConcurrentFirstWritesNeverSurfaceADuplicateKeyFailure() throws Exception {
        UUID assetId = persistUpload();
        int writers = 8;
        CyclicBarrier startTogether = new CyclicBarrier(writers);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Future<Throwable>> results = new ArrayList<>();

        try {
            for (int index = 0; index < writers; index++) {
                long position = 100L * (index + 1);
                results.add(pool.submit(() -> {
                    try {
                        startTogether.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                        upsert(assetId, "user-1", position, false, FIRST_WRITE);
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    }
                }));
            }
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> result : results) {
                Throwable failure = result.get(AWAIT_SECONDS, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            assertThat(failures).isEmpty();
        } finally {
            pool.shutdownNow();
        }

        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(read(assetId, "user-1"))
                .get()
                .extracting(AssetPlaybackProgressSnapshot::positionMs)
                .isIn(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L);
    }

    // ------------------------------------------------------ concurrent update

    @Test
    void twoConcurrentUpdatesOfAnExistingRowBothSucceedAndTheLastCommittedWriteWins() throws Exception {
        UUID assetId = persistUpload();
        upsert(assetId, "user-1", 10L, false, FIRST_WRITE);

        ConcurrentOutcome outcome = runInterleavedWrites(
                assetId, "user-1",
                new Write(2000L, false, FIRST_WRITE),
                new Write(3000L, true, SECOND_WRITE)
        );

        assertThat(outcome.failures()).isEmpty();
        assertThat(rowCount(assetId)).isEqualTo(1);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(3000L, true, SECOND_WRITE));
    }

    // ------------------------------------------------------------- isolation

    @Test
    void concurrentWritesForDifferentUsersDoNotOverwriteOneAnother() throws Exception {
        UUID assetId = persistUpload();

        ConcurrentOutcome outcome = runConcurrently(
                () -> upsert(assetId, "user-1", 111L, false, FIRST_WRITE),
                () -> upsert(assetId, "user-2", 222L, true, SECOND_WRITE)
        );

        assertThat(outcome.failures()).isEmpty();
        assertThat(rowCount(assetId)).isEqualTo(2);
        assertThat(read(assetId, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(111L, false, FIRST_WRITE));
        assertThat(read(assetId, "user-2"))
                .contains(new AssetPlaybackProgressSnapshot(222L, true, SECOND_WRITE));
    }

    @Test
    void concurrentWritesForDifferentAssetsDoNotOverwriteOneAnother() throws Exception {
        UUID firstAsset = persistUpload();
        UUID secondAsset = persistYoutube();

        ConcurrentOutcome outcome = runConcurrently(
                () -> upsert(firstAsset, "user-1", 333L, false, FIRST_WRITE),
                () -> upsert(secondAsset, "user-1", 444L, true, SECOND_WRITE)
        );

        assertThat(outcome.failures()).isEmpty();
        assertThat(rowCount(firstAsset)).isEqualTo(1);
        assertThat(rowCount(secondAsset)).isEqualTo(1);
        assertThat(read(firstAsset, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(333L, false, FIRST_WRITE));
        assertThat(read(secondAsset, "user-1"))
                .contains(new AssetPlaybackProgressSnapshot(444L, true, SECOND_WRITE));
    }

    // ------------------------------------------------------- deletion cleanup

    @Test
    void deletingTheAssetStillRemovesEveryProgressRow() {
        UUID assetId = persistUpload();
        upsert(assetId, "user-1", 1L, false, FIRST_WRITE);
        upsert(assetId, "user-2", 2L, true, FIRST_WRITE);
        assertThat(rowCount(assetId)).isEqualTo(2);

        transactionTemplate.executeWithoutResult(status -> progressStore.deleteForAsset(assetId));

        assertThat(rowCount(assetId)).isZero();
    }

    // --------------------------------------------------------------- helpers

    private record Write(long positionMs, boolean completed, Instant updatedAt) {
    }

    private record ConcurrentOutcome(List<Throwable> failures) {
    }

    /**
     * Forces the hostile interleaving: the first transaction issues its statement and stays open
     * while the second transaction issues its own statement against the same key, so the second
     * write must contend on the row before the first one commits.
     */
    private ConcurrentOutcome runInterleavedWrites(UUID assetId, String userId, Write first, Write second)
            throws Exception {
        CountDownLatch firstStatementIssued = new CountDownLatch(1);
        CountDownLatch secondWriterReleased = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = new ArrayList<>();

        try {
            Future<Throwable> firstWriter = pool.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        progressStore.upsert(
                                assetId, userId, first.positionMs(), first.completed(), first.updatedAt()
                        );
                        firstStatementIssued.countDown();
                        try {
                            secondWriterReleased.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                            // Give the second writer time to block on the contended row.
                            Thread.sleep(400L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });

            Future<Throwable> secondWriter = pool.submit(() -> {
                try {
                    firstStatementIssued.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                    secondWriterReleased.countDown();
                    transactionTemplate.executeWithoutResult(status -> progressStore.upsert(
                            assetId, userId, second.positionMs(), second.completed(), second.updatedAt()
                    ));
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });

            collect(failures, firstWriter);
            collect(failures, secondWriter);
        } finally {
            pool.shutdownNow();
        }
        return new ConcurrentOutcome(failures);
    }

    private ConcurrentOutcome runConcurrently(Runnable first, Runnable second) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = new ArrayList<>();

        try {
            List<Future<Throwable>> writers = List.of(
                    pool.submit(() -> guarded(startTogether, first)),
                    pool.submit(() -> guarded(startTogether, second))
            );
            for (Future<Throwable> writer : writers) {
                collect(failures, writer);
            }
        } finally {
            pool.shutdownNow();
        }
        return new ConcurrentOutcome(failures);
    }

    private Throwable guarded(CyclicBarrier startTogether, Runnable write) {
        try {
            startTogether.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            write.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void collect(List<Throwable> failures, Future<Throwable> writer) throws Exception {
        Throwable failure = writer.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        if (failure != null) {
            failures.add(failure);
        }
    }

    private AssetPlaybackProgressSnapshot upsert(
            UUID assetId, String userId, long positionMs, boolean completed, Instant updatedAt
    ) {
        return transactionTemplate.execute(status ->
                progressStore.upsert(assetId, userId, positionMs, completed, updatedAt));
    }

    private java.util.Optional<AssetPlaybackProgressSnapshot> read(UUID assetId, String userId) {
        return transactionTemplate.execute(status -> progressStore.find(assetId, userId));
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
        transactionTemplate.executeWithoutResult(status -> assetStore.save(Asset.uploaded(
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
        )));
        return assetId;
    }

    private UUID persistYoutube() {
        UUID assetId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> assetStore.saveYoutube(Asset.youtube(
                assetId,
                "vid" + assetId.toString().replace("-", "").substring(0, 8),
                "YouTube video",
                AssetStatus.SEARCHABLE,
                workspaceId
        )));
        return assetId;
    }
}
