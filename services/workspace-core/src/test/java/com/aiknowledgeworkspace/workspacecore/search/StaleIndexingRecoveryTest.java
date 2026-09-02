package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module.SearchAssetPortAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexDocument;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexWriteOperation;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexWriter;
import com.aiknowledgeworkspace.workspacecore.search.application.result.IndexingRecoveryResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.ExecuteIndexJobApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.application.service.StaleIndexingRecoveryService;
import com.aiknowledgeworkspace.workspacecore.search.application.service.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovery of indexing jobs whose worker died mid-attempt.
 *
 * <p>The database cannot tell from {@code INDEXING} whether Elasticsearch received nothing, part of
 * the projection, or all of it. These tests therefore exercise each of those crash windows and
 * assert the same outcome: the asset ends up with exactly one correct set of documents.
 *
 * <p>Elasticsearch is replaced by an in-memory index that models the two properties the real
 * adapter relies on — delete-by-asset removes that asset's documents, and a bulk {@code index}
 * action writes by document id, replacing any document already under it. The real adapter's
 * request shape is covered beside it in {@code ElasticsearchTranscriptAdapterBulkPayloadTest}.
 *
 * <p>The stale threshold is deliberately not the production default, so passing proves the
 * configured value is what recovery and the stuck gauge both read.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-stale-indexing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "workspace.search.indexing.stale-age=90s",
        "workspace.search.indexing.recovery-enabled=true",
        "workspace.search.indexing.recovery-interval=60s",
        "workspace.search.indexing.recovery-batch-size=20"
})
@Transactional
class StaleIndexingRecoveryTest {

    private static final Duration STALE_AFTER = Duration.ofSeconds(90);

    @TestConfiguration
    static class InMemorySearchIndexConfiguration {

        @Bean
        @Primary
        InMemoryTranscriptIndex inMemoryTranscriptIndex() {
            return new InMemoryTranscriptIndex();
        }
    }

    /** Models the Elasticsearch semantics the indexing write path depends on, and nothing else. */
    static final class InMemoryTranscriptIndex implements TranscriptIndexWriter {

        private final Map<String, TranscriptIndexDocument> documents = new LinkedHashMap<>();
        private final List<List<TranscriptIndexWriteOperation>> batches = new ArrayList<>();
        private int failAfterOperations = -1;

        @Override
        public void ensureTranscriptIndexExists() {
        }

        @Override
        public void deleteTranscriptRowsForAsset(UUID assetId) {
            documents.entrySet().removeIf(entry -> entry.getValue().assetId().equals(assetId));
        }

        @Override
        public void indexTranscriptRows(List<TranscriptIndexWriteOperation> operations) {
            batches.add(List.copyOf(operations));
            int written = 0;
            for (TranscriptIndexWriteOperation operation : operations) {
                if (failAfterOperations >= 0 && written == failAfterOperations) {
                    throw new IllegalStateException("simulated bulk interruption");
                }
                documents.put(operation.documentId(), operation.document());
                written++;
            }
        }

        @Override
        public void refreshTranscriptIndex() {
        }

        void failAfter(int operations) {
            this.failAfterOperations = operations;
        }

        void reset() {
            documents.clear();
            batches.clear();
            failAfterOperations = -1;
        }

        void seedExactly(List<TranscriptIndexWriteOperation> operations) {
            operations.forEach(operation -> documents.put(operation.documentId(), operation.document()));
        }

        List<String> documentIdsFor(UUID assetId) {
            return documents.entrySet().stream()
                    .filter(entry -> entry.getValue().assetId().equals(assetId))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }

        List<String> textsFor(UUID assetId) {
            return documents.values().stream()
                    .filter(document -> document.assetId().equals(assetId))
                    .map(TranscriptIndexDocument::text)
                    .sorted()
                    .toList();
        }

        List<List<TranscriptIndexWriteOperation>> batches() {
            return List.copyOf(batches);
        }
    }

    @Autowired
    private StaleIndexingRecoveryService staleIndexingRecoveryService;

    @Autowired
    private ExecuteIndexJobApplicationService executeIndexJobApplicationService;

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private SearchIndexingProperties indexingProperties;

    @Autowired
    private InMemoryTranscriptIndex transcriptIndex;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private SearchAssetPortAdapter searchAssetPortAdapter;

    private final TranscriptSnapshotFingerprintService fingerprintService = new TranscriptSnapshotFingerprintService();

    @BeforeEach
    void setUp() {
        transcriptIndex.reset();
    }

    // ------------------------------------------------------------- eligibility

    @Test
    void aClaimYoungerThanTheThresholdIsLeftToTheWorkerThatHoldsIt() {
        UUID assetId = persistAsset();
        AssetSearchIndexJob job = claimedJob(assetId, transcriptRows("one"), Instant.now().minusSeconds(10));

        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(result.eligible()).isZero();
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(transcriptIndex.batches()).isEmpty();
    }

    @Test
    void terminalJobsAreNeverReactivated() {
        UUID assetId = persistAsset();
        Instant longAgo = Instant.now().minus(Duration.ofDays(1));
        AssetSearchIndexJob indexed = terminalJob(assetId, "fingerprint-indexed", longAgo,
                job -> job.markIndexed(longAgo));
        AssetSearchIndexJob failed = terminalJob(assetId, "fingerprint-failed", longAgo,
                job -> job.markFailed("ELASTICSEARCH_RESPONSE_INVALID"));
        AssetSearchIndexJob superseded = terminalJob(assetId, "fingerprint-superseded", longAgo,
                AssetSearchIndexJob::markSuperseded);

        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(result.eligible()).isZero();
        assertThat(status(indexed)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(status(failed)).isEqualTo(AssetSearchIndexJobStatus.FAILED);
        assertThat(status(superseded)).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(transcriptIndex.batches()).isEmpty();
    }

    @Test
    void aClaimExactlyAtTheCutoffQualifiesAndTheGaugeAgreesOnTheSameBoundary() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = now.minus(STALE_AFTER);
        UUID assetId = persistAsset();
        List<IndexingTranscriptRow> rows = transcriptRows("boundary");
        AssetSearchIndexJob atCutoff = claimedJob(assetId, rows, cutoff);
        UUID otherAssetId = persistAsset();
        AssetSearchIndexJob justInside = claimedJob(otherAssetId, rows, cutoff.plusMillis(1));

        IndexingRecoveryResult result = recoveryServiceAt(now).recoverStaleIndexingJobs();

        assertThat(result.eligible()).isEqualTo(1);
        assertThat(status(atCutoff)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(status(justInside)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(searchIndexJobRepository.loadBacklogSnapshot(cutoff).stuckIndexing()).isZero();
    }

    // ----------------------------------------------------------- crash windows

    @Test
    void crashBeforeAnyElasticsearchWriteIsReplayedThroughTheCanonicalPath() {
        UUID assetId = persistAsset();
        List<IndexingTranscriptRow> rows = transcriptRows("alpha", "beta");
        AssetSearchIndexJob job = claimedJob(assetId, rows, Instant.now().minus(Duration.ofMinutes(42)));

        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(result.recovered()).isEqualTo(1);
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(transcriptIndex.textsFor(assetId)).containsExactly("alpha", "beta");
    }

    /**
     * The central case: Elasticsearch already holds the complete projection, and the crash happened
     * before the job could record it. Replay must converge on the same documents rather than adding
     * a second copy of each row.
     */
    @Test
    void elasticsearchAlreadyCompleteWithoutAFinalisedJobReplaysWithoutDuplicating() {
        UUID assetId = persistAsset();
        List<IndexingTranscriptRow> rows = transcriptRows("alpha", "beta");
        AssetSearchIndexJob job = claimedJob(assetId, rows, Instant.now().minus(Duration.ofMinutes(42)));
        transcriptIndex.seedExactly(expectedOperations(assetId, rows));
        List<String> documentIdsBefore = transcriptIndex.documentIdsFor(assetId);

        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(result.recovered()).isEqualTo(1);
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(transcriptIndex.documentIdsFor(assetId))
                .as("replay must land on the same document ids, not create new ones")
                .isEqualTo(documentIdsBefore)
                .hasSize(rows.size());
        assertThat(transcriptIndex.textsFor(assetId)).containsExactly("alpha", "beta");
    }

    @Test
    void aPartiallyWrittenAttemptConvergesAndLeavesNoDocumentFromTheOlderProjection() {
        UUID assetId = persistAsset();
        List<IndexingTranscriptRow> rows = transcriptRows("alpha", "beta");
        AssetSearchIndexJob job = claimedJob(assetId, rows, Instant.now().minus(Duration.ofMinutes(42)));
        // One row of this attempt landed, plus a document from a transcript that no longer exists.
        transcriptIndex.seedExactly(expectedOperations(assetId, rows.subList(0, 1)));
        transcriptIndex.seedExactly(List.of(new TranscriptIndexWriteOperation(
                assetId + "-orphaned-row", document(assetId, "orphaned-row", 7, "removed sentence"))));

        staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(transcriptIndex.textsFor(assetId))
                .as("delete-by-asset then full write converges on exactly the canonical set")
                .containsExactly("alpha", "beta");
    }

    @Test
    void replayReusesTheSameDeterministicDocumentIdentity() {
        UUID assetId = persistAsset();
        List<IndexingTranscriptRow> rows = transcriptRows("alpha", "beta");
        AssetSearchIndexJob job = claimedJob(assetId, rows, Instant.now().minus(Duration.ofMinutes(42)));

        executeIndexJobApplicationService.execute(job.getId());
        // The attempt reached Elasticsearch but the finalisation never landed, so the row is back
        // to what a crash would have left behind: still claimed, and now stale.
        forceIndexingClaim(job, Instant.now().minus(Duration.ofMinutes(42)));
        staleIndexingRecoveryService.recoverStaleIndexingJobs();

        List<List<TranscriptIndexWriteOperation>> batches = transcriptIndex.batches();
        assertThat(batches).hasSize(2);
        assertThat(batches.get(1).stream().map(TranscriptIndexWriteOperation::documentId).toList())
                .isEqualTo(batches.get(0).stream().map(TranscriptIndexWriteOperation::documentId).toList());
    }

    @Test
    void aStuckJobCannotOverwriteANewerCanonicalTranscript() {
        UUID assetId = persistAsset();
        List<IndexingTranscriptRow> oldRows = transcriptRows("old sentence");
        AssetSearchIndexJob stuckJob = claimedJob(assetId, oldRows, Instant.now().minus(Duration.ofMinutes(42)));
        List<IndexingTranscriptRow> newRows = transcriptRows("new sentence", "and another");
        stubCanonicalSource(assetId, newRows);
        transcriptIndex.seedExactly(expectedOperations(assetId, newRows));

        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(result.recovered()).isEqualTo(1);
        assertThat(status(stuckJob))
                .as("the stale fingerprint loses to the current transcript")
                .isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(transcriptIndex.textsFor(assetId))
                .as("the newer projection is left untouched")
                .containsExactly("and another", "new sentence");
        assertThat(transcriptIndex.batches()).isEmpty();
    }

    // ------------------------------------------------------------- concurrency

    @Test
    void twoRecoveryWorkersCannotBothReplayTheSameStaleJob() {
        UUID assetId = persistAsset();
        AssetSearchIndexJob job = claimedJob(assetId, transcriptRows("alpha"),
                Instant.now().minus(Duration.ofMinutes(42)));
        Instant cutoff = Instant.now().minus(STALE_AFTER);

        int firstWorker = searchIndexJobRepository.claimStaleIndexingJob(
                job.getId(), AssetSearchIndexJobStatus.INDEXING, cutoff, Instant.now());
        int secondWorker = searchIndexJobRepository.claimStaleIndexingJob(
                job.getId(), AssetSearchIndexJobStatus.INDEXING, cutoff, Instant.now());

        assertThat(firstWorker).isEqualTo(1);
        assertThat(secondWorker).isZero();
        assertThat(staleIndexingRecoveryService.recoverStaleIndexingJobs().eligible()).isZero();
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
    }

    @Test
    void aJobThatFinishedBetweenTheScanAndTheClaimIsSkipped() {
        UUID assetId = persistAsset();
        AssetSearchIndexJob job = claimedJob(assetId, transcriptRows("alpha"),
                Instant.now().minus(Duration.ofMinutes(42)));
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        executeIndexJobApplicationService.execute(job.getId());

        int claimed = searchIndexJobRepository.claimStaleIndexingJob(
                job.getId(), AssetSearchIndexJobStatus.INDEXING, cutoff, Instant.now());

        assertThat(claimed).isZero();
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
    }

    // ------------------------------------------------------------ failure path

    @Test
    void aFailedReplayLeavesTheJobClaimedAndRecoverableByALaterPass() {
        UUID assetId = persistAsset();
        AssetSearchIndexJob job = claimedJob(assetId, transcriptRows("alpha", "beta"),
                Instant.now().minus(Duration.ofMinutes(42)));
        transcriptIndex.failAfter(1);

        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.recovered()).isZero();
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);

        transcriptIndex.failAfter(-1);
        forceClaim(job, Instant.now().minus(Duration.ofMinutes(42)));
        assertThat(staleIndexingRecoveryService.recoverStaleIndexingJobs().recovered()).isEqualTo(1);
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(transcriptIndex.textsFor(assetId)).containsExactly("alpha", "beta");
    }

    @Test
    void disabledRecoveryNeitherScansNorReplays() {
        UUID assetId = persistAsset();
        AssetSearchIndexJob job = claimedJob(assetId, transcriptRows("alpha"),
                Instant.now().minus(Duration.ofMinutes(42)));
        SearchIndexingProperties disabled = new SearchIndexingProperties();
        disabled.setStaleAge(STALE_AFTER);

        IndexingRecoveryResult result = new StaleIndexingRecoveryService(
                searchIndexJobRepository, executeIndexJobApplicationService, disabled, Clock.systemUTC()
        ).recoverStaleIndexingJobs();

        assertThat(result.disabled()).isTrue();
        assertThat(status(job)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(transcriptIndex.batches()).isEmpty();
    }

    // -------------------------------------------------------------------- drill

    @Test
    void recoveryDrillResolvesEveryStuckJobAccordingToItsOwnSource() {
        UUID recentAsset = persistAsset();
        UUID beforeWriteAsset = persistAsset();
        UUID alreadyIndexedAsset = persistAsset();
        UUID supersededAsset = persistAsset();
        List<IndexingTranscriptRow> rows = transcriptRows("alpha", "beta");
        Instant stale = Instant.now().minus(Duration.ofMinutes(42));

        AssetSearchIndexJob recent = claimedJob(recentAsset, rows, Instant.now().minusSeconds(5));
        AssetSearchIndexJob beforeWrite = claimedJob(beforeWriteAsset, rows, stale);
        AssetSearchIndexJob alreadyWritten = claimedJob(alreadyIndexedAsset, rows, stale);
        transcriptIndex.seedExactly(expectedOperations(alreadyIndexedAsset, rows));
        AssetSearchIndexJob supersededJob = claimedJob(supersededAsset, transcriptRows("old"), stale);
        stubCanonicalSource(supersededAsset, rows);

        double stuckBefore = meterRegistry.get("project3.search.index.stuck")
                .tag("status", "indexing").gauge().value();
        IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();
        long stuckAfter = searchIndexJobRepository
                .loadBacklogSnapshot(Instant.now().minus(indexingProperties.getStaleAge())).stuckIndexing();
        assertThat(stuckBefore).isEqualTo(3.0);
        assertThat(stuckAfter).isZero();
        assertThat(result.eligible()).isEqualTo(3);
        assertThat(result.recovered()).isEqualTo(3);
        assertThat(status(recent)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(status(beforeWrite)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(status(alreadyWritten)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(status(supersededJob)).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(transcriptIndex.textsFor(beforeWriteAsset)).containsExactly("alpha", "beta");
        assertThat(transcriptIndex.textsFor(alreadyIndexedAsset)).containsExactly("alpha", "beta");
    }

    // ------------------------------------------------------------------ helpers

    private StaleIndexingRecoveryService recoveryServiceAt(Instant now) {
        return new StaleIndexingRecoveryService(
                searchIndexJobRepository,
                executeIndexJobApplicationService,
                indexingProperties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    /** A job already claimed for indexing, exactly as a worker leaves it before it dies. */
    private AssetSearchIndexJob claimedJob(UUID assetId, List<IndexingTranscriptRow> rows, Instant claimedAt) {
        stubCanonicalSource(assetId, rows);
        AssetSearchIndexJob job = new AssetSearchIndexJob(assetId, fingerprintService.fingerprint(rows));
        job.attachRequestOutboxEvent(UUID.randomUUID());
        job.markIndexing();
        AssetSearchIndexJob saved = searchIndexJobRepository.save(job);
        forceClaim(saved, claimedAt);
        return saved;
    }

    private AssetSearchIndexJob terminalJob(
            UUID assetId,
            String fingerprint,
            Instant at,
            java.util.function.Consumer<AssetSearchIndexJob> terminalTransition
    ) {
        AssetSearchIndexJob job = new AssetSearchIndexJob(assetId, fingerprint);
        job.markIndexing();
        terminalTransition.accept(job);
        AssetSearchIndexJob saved = searchIndexJobRepository.save(job);
        forceClaim(saved, at);
        return saved;
    }

    private void stubCanonicalSource(UUID assetId, List<IndexingTranscriptRow> rows) {
        when(searchAssetPortAdapter.findCurrentIndexingSource(assetId))
                .thenReturn(Optional.of(new IndexingAssetSource(assetId, UUID.randomUUID(), "Lecture", rows)));
    }

    /**
     * Bulk update so the row carries a claim instant older than this test run; the entity callback
     * would otherwise stamp the current time and no claim could ever look stale.
     */
    private void forceClaim(AssetSearchIndexJob job, Instant claimedAt) {
        entityManager.flush();
        entityManager.createQuery("update AssetSearchIndexJob j set j.updatedAt = :claimedAt where j.id = :id")
                .setParameter("claimedAt", claimedAt)
                .setParameter("id", job.getId())
                .executeUpdate();
        entityManager.clear();
    }

    /** Puts the row back into the state a crash mid-attempt leaves: claimed, and already stale. */
    private void forceIndexingClaim(AssetSearchIndexJob job, Instant claimedAt) {
        entityManager.flush();
        entityManager.createQuery("""
                        update AssetSearchIndexJob j
                        set j.status = :indexing, j.updatedAt = :claimedAt, j.indexedAt = null
                        where j.id = :id
                        """)
                .setParameter("indexing", AssetSearchIndexJobStatus.INDEXING)
                .setParameter("claimedAt", claimedAt)
                .setParameter("id", job.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private List<IndexingTranscriptRow> transcriptRows(String... texts) {
        List<IndexingTranscriptRow> rows = new ArrayList<>();
        for (int index = 0; index < texts.length; index++) {
            rows.add(new IndexingTranscriptRow(
                    "row-" + index, "video-1", index, (long) index * 1000, (long) (index + 1) * 1000,
                    texts[index], "2026-09-02T00:00:00Z"
            ));
        }
        return List.copyOf(rows);
    }

    private List<TranscriptIndexWriteOperation> expectedOperations(UUID assetId, List<IndexingTranscriptRow> rows) {
        return rows.stream()
                .map(row -> new TranscriptIndexWriteOperation(
                        assetId + "-" + row.id(),
                        document(assetId, row.id(), row.segmentIndex(), row.text())
                ))
                .toList();
    }

    private TranscriptIndexDocument document(UUID assetId, String rowId, Integer segmentIndex, String text) {
        return new TranscriptIndexDocument(
                assetId, UUID.randomUUID(), "Lecture", rowId, segmentIndex, 0L, 1000L, text,
                "2026-09-02T00:00:00Z", "TRANSCRIPT_READY"
        );
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
                AssetStatus.TRANSCRIPT_READY,
                workspace.getId(),
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        ));
        return assetId;
    }

    private AssetSearchIndexJobStatus status(AssetSearchIndexJob job) {
        entityManager.flush();
        entityManager.clear();
        return searchIndexJobRepository.findById(job.getId()).orElseThrow().getStatus();
    }
}
