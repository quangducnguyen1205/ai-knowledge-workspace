package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.IndexingBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query semantics behind the async backlog gauges. The SQL is the feature here, so these run
 * against the real schema rather than a stubbed store: status filtering, the stuck threshold
 * boundary, the oldest timestamp, empty tables, mixed states, and the exclusion of terminal
 * history that would otherwise turn an operational backlog into a row count of everything that
 * ever happened.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-async-backlog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class AsyncBacklogQueryTest {

    private static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    @Autowired
    private OutboxEventStore outboxEventRepository;

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private ProcessingJobStore processingJobRepository;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Anchored to real time, and truncated so a round trip through the database compares exactly:
     * rows created by these fixtures are stamped by the entity callbacks with the actual clock, so
     * a fabricated "now" in the future would make backdated rows look newer than untouched ones.
     */
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private Instant stuckBefore() {
        return now.minus(STUCK_AFTER);
    }

    // ------------------------------------------------------------------ outbox

    @Test
    void emptyTablesReportZeroBacklogWithNoOldestTimestamp() {
        assertThat(outboxEventRepository.loadBacklogSnapshot(stuckBefore()))
                .isEqualTo(OutboxBacklogSnapshot.empty());
        assertThat(searchIndexJobRepository.loadBacklogSnapshot(stuckBefore()))
                .isEqualTo(IndexingBacklogSnapshot.empty());
        assertThat(processingJobRepository.loadBacklogSnapshot())
                .isEqualTo(ProcessingBacklogSnapshot.empty());
    }

    @Test
    void pendingAndFailedOutboxEventsAreCountedByStatus() {
        persistOutboxEvent();
        persistOutboxEvent();
        UUID failedId = persistOutboxEvent();
        markStatus(failedId, OutboxEventStatus.FAILED);

        OutboxBacklogSnapshot snapshot = outboxEventRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.pending()).isEqualTo(2);
        assertThat(snapshot.failed()).isEqualTo(1);
        assertThat(snapshot.publishing()).isZero();
        assertThat(snapshot.stuckPublishing()).isZero();
        assertThat(snapshot.oldestStuckPublishingSince()).isNull();
    }

    @Test
    void recentlyClaimedPublishingEventIsBacklogButNotYetStuck() {
        UUID eventId = persistOutboxEvent();
        claimForPublishing(eventId, now.minus(Duration.ofMinutes(1)));

        OutboxBacklogSnapshot snapshot = outboxEventRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.publishing()).isEqualTo(1);
        assertThat(snapshot.stuckPublishing()).isZero();
        assertThat(snapshot.oldestStuckPublishingAge(now)).isZero();
    }

    @Test
    void longHeldPublishingClaimIsStuckAndReportsItsClaimInstantAsTheOldest() {
        UUID recentId = persistOutboxEvent();
        UUID oldestId = persistOutboxEvent();
        UUID middleId = persistOutboxEvent();
        Instant oldestClaim = now.minus(Duration.ofMinutes(42));
        claimForPublishing(recentId, now.minus(Duration.ofSeconds(30)));
        claimForPublishing(oldestId, oldestClaim);
        claimForPublishing(middleId, now.minus(Duration.ofMinutes(9)));

        OutboxBacklogSnapshot snapshot = outboxEventRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.publishing()).isEqualTo(3);
        assertThat(snapshot.stuckPublishing()).isEqualTo(2);
        assertThat(snapshot.oldestStuckPublishingSince()).isEqualTo(oldestClaim);
        assertThat(snapshot.oldestStuckPublishingAge(now)).isEqualTo(Duration.ofMinutes(42));
    }

    @Test
    void aClaimExactlyAtTheThresholdCountsAsStuck() {
        UUID atThreshold = persistOutboxEvent();
        UUID justInside = persistOutboxEvent();
        claimForPublishing(atThreshold, stuckBefore());
        claimForPublishing(justInside, stuckBefore().plusMillis(1));

        OutboxBacklogSnapshot snapshot = outboxEventRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.publishing()).isEqualTo(2);
        assertThat(snapshot.stuckPublishing()).isEqualTo(1);
        assertThat(snapshot.oldestStuckPublishingSince()).isEqualTo(stuckBefore());
    }

    @Test
    void publishedHistoryDoesNotInflateTheOperationalBacklog() {
        for (int index = 0; index < 100; index++) {
            UUID publishedId = persistOutboxEvent();
            markStatus(publishedId, OutboxEventStatus.PUBLISHED);
        }
        persistOutboxEvent();

        OutboxBacklogSnapshot snapshot = outboxEventRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.pending()).isEqualTo(1);
        assertThat(snapshot.publishing()).isZero();
        assertThat(snapshot.failed()).isZero();
    }

    // ---------------------------------------------------------------- indexing

    @Test
    void indexingJobsAreCountedByStatusAndAnOldClaimIsStuck() {
        UUID assetId = persistAsset();
        persistIndexJob(assetId, "fingerprint-pending");
        AssetSearchIndexJob recentlyClaimed = persistIndexJob(assetId, "fingerprint-recent");
        AssetSearchIndexJob longClaimed = persistIndexJob(assetId, "fingerprint-old");
        Instant oldClaim = now.minus(Duration.ofMinutes(30));
        claimForIndexing(recentlyClaimed, now.minus(Duration.ofSeconds(20)));
        claimForIndexing(longClaimed, oldClaim);

        IndexingBacklogSnapshot snapshot = searchIndexJobRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.pending()).isEqualTo(1);
        assertThat(snapshot.indexing()).isEqualTo(2);
        assertThat(snapshot.stuckIndexing()).isEqualTo(1);
        assertThat(snapshot.oldestStuckIndexingSince()).isEqualTo(oldClaim);
        assertThat(snapshot.oldestStuckIndexingAge(now)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void terminalIndexingHistoryIsExcludedWhileFailedRemainsVisible() {
        UUID assetId = persistAsset();
        AssetSearchIndexJob indexed = persistIndexJob(assetId, "fingerprint-indexed");
        AssetSearchIndexJob superseded = persistIndexJob(assetId, "fingerprint-superseded");
        AssetSearchIndexJob failed = persistIndexJob(assetId, "fingerprint-failed");
        indexed.markIndexing();
        indexed.markIndexed(now);
        superseded.markSuperseded();
        failed.markFailed("ELASTICSEARCH_RESPONSE_INVALID");
        searchIndexJobRepository.save(indexed);
        searchIndexJobRepository.save(superseded);
        searchIndexJobRepository.save(failed);

        IndexingBacklogSnapshot snapshot = searchIndexJobRepository.loadBacklogSnapshot(stuckBefore());

        assertThat(snapshot.pending()).isZero();
        assertThat(snapshot.indexing()).isZero();
        assertThat(snapshot.failed()).isEqualTo(1);
    }

    // -------------------------------------------------------------- processing

    @Test
    void processingBacklogCountsOnlyWaitingJobsAndReportsTheLongestWait() {
        UUID waitingAsset = persistAsset();
        UUID longestWaitingAsset = persistAsset();
        UUID finishedAsset = persistAsset();
        persistProcessingJob(waitingAsset, ProcessingJobStatus.PENDING);
        ProcessingJob longestWaiting = persistProcessingJob(longestWaitingAsset, ProcessingJobStatus.PENDING);
        persistProcessingJob(finishedAsset, ProcessingJobStatus.SUCCEEDED);
        Instant waitingSince = now.minus(Duration.ofMinutes(20));
        backdateUpdatedAt(ProcessingJob.class, longestWaiting.getId(), waitingSince);

        ProcessingBacklogSnapshot snapshot = processingJobRepository.loadBacklogSnapshot();

        assertThat(snapshot.pending()).isEqualTo(2);
        assertThat(snapshot.oldestPendingSince()).isEqualTo(waitingSince);
        assertThat(snapshot.oldestPendingWait(now)).isEqualTo(Duration.ofMinutes(20));
    }

    // ----------------------------------------------------------- restart safety

    @Test
    void backlogReadingsComeFromPersistedRowsRatherThanFromProcessLocalCounters() {
        UUID assetId = persistAsset();
        persistOutboxEvent();
        persistIndexJob(assetId, "fingerprint-restart");
        persistProcessingJob(assetId, ProcessingJobStatus.PENDING);
        entityManager.flush();

        OutboxBacklogSnapshot outboxBefore = outboxEventRepository.loadBacklogSnapshot(stuckBefore());
        IndexingBacklogSnapshot indexingBefore = searchIndexJobRepository.loadBacklogSnapshot(stuckBefore());
        ProcessingBacklogSnapshot processingBefore = processingJobRepository.loadBacklogSnapshot();

        // Dropping every managed instance is the closest in-test equivalent of a fresh process:
        // nothing in memory survives, so a reading that still matches can only come from the rows.
        entityManager.clear();

        assertThat(outboxEventRepository.loadBacklogSnapshot(stuckBefore())).isEqualTo(outboxBefore);
        assertThat(searchIndexJobRepository.loadBacklogSnapshot(stuckBefore())).isEqualTo(indexingBefore);
        assertThat(processingJobRepository.loadBacklogSnapshot()).isEqualTo(processingBefore);
        assertThat(outboxBefore.pending()).isEqualTo(1);
        assertThat(indexingBefore.pending()).isEqualTo(1);
        assertThat(processingBefore.pending()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ fixtures

    private UUID persistOutboxEvent() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                "asset.processing.requested",
                1,
                "ASSET",
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "{}"
        );
        return outboxEventRepository.save(event).getId();
    }

    /** Enters PUBLISHING exactly the way the relay does, stamping the claim instant. */
    private void claimForPublishing(UUID eventId, Instant claimedAt) {
        int claimed = outboxEventRepository.markPublishing(
                eventId,
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PUBLISHING,
                claimedAt
        );
        assertThat(claimed).isEqualTo(1);
    }

    private void markStatus(UUID eventId, OutboxEventStatus status) {
        entityManager.flush();
        entityManager.createQuery("update OutboxEvent event set event.status = :status where event.id = :id")
                .setParameter("status", status)
                .setParameter("id", eventId)
                .executeUpdate();
    }

    private UUID persistAsset() {
        Workspace workspace = workspaceRepository.save(new Workspace(
                UUID.randomUUID(), "Algorithms", "user-1", false
        ));
        UUID assetId = UUID.randomUUID();
        assetRepository.save(Asset.uploaded(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.PROCESSING,
                workspace.getId(),
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        ));
        return assetId;
    }

    private AssetSearchIndexJob persistIndexJob(UUID assetId, String fingerprint) {
        return searchIndexJobRepository.save(new AssetSearchIndexJob(assetId, fingerprint));
    }

    private void claimForIndexing(AssetSearchIndexJob job, Instant claimedAt) {
        job.markIndexing();
        searchIndexJobRepository.save(job);
        backdateUpdatedAt(AssetSearchIndexJob.class, job.getId(), claimedAt);
    }

    private ProcessingJob persistProcessingJob(UUID assetId, ProcessingJobStatus status) {
        ProcessingJob job = new ProcessingJob(assetId, status, null);
        job.setProcessingRequestEventId(UUID.randomUUID());
        return processingJobRepository.save(job);
    }

    /**
     * Bulk update so the row carries a claim/wait instant older than this test run; entity
     * callbacks would otherwise stamp the current time and no state could ever look old.
     */
    private void backdateUpdatedAt(Class<?> entityType, UUID id, Instant updatedAt) {
        entityManager.flush();
        entityManager.createQuery(
                        "update %s entity set entity.updatedAt = :updatedAt where entity.id = :id"
                                .formatted(entityType.getSimpleName()))
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
