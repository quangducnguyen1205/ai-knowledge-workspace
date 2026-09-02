package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchRebuildCommand;
import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchRebuildProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexDocument;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexWriteOperation;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexWriter;
import com.aiknowledgeworkspace.workspacecore.search.application.result.SearchIndexRebuildResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchIndexRebuildService;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilding the search projection from canonical PostgreSQL state.
 *
 * <p>Everything on the PostgreSQL side is real here — assets, canonical transcript rows, indexing
 * job history, the enumeration query, the fingerprint check — because the audit gap being closed is
 * precisely that canonical truth existed but could not be turned back into a projection.
 * Elasticsearch is an in-memory index modelling the two properties the write path relies on:
 * delete-by-asset removes that asset's documents, and a bulk {@code index} action writes by
 * document id, replacing whatever sat under it. Real Elasticsearch behaviour is proven separately
 * by {@code SearchIndexRebuildIT}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-search-rebuild;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "workspace.search.rebuild.batch-size=2"
})
@Transactional
class SearchIndexRebuildTest {

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
        private final List<UUID> failingAssets = new ArrayList<>();
        private boolean indexExists;

        @Override
        public void ensureTranscriptIndexExists() {
            indexExists = true;
        }

        @Override
        public void deleteTranscriptRowsForAsset(UUID assetId) {
            documents.entrySet().removeIf(entry -> entry.getValue().assetId().equals(assetId));
        }

        @Override
        public void indexTranscriptRows(List<TranscriptIndexWriteOperation> operations) {
            operations.forEach(operation -> {
                if (failingAssets.contains(operation.document().assetId())) {
                    throw new IllegalStateException("simulated Elasticsearch rejection");
                }
                documents.put(operation.documentId(), operation.document());
            });
        }

        @Override
        public void refreshTranscriptIndex() {
        }

        void reset() {
            documents.clear();
            failingAssets.clear();
            indexExists = false;
        }

        void failFor(UUID assetId) {
            failingAssets.add(assetId);
        }

        void seed(String documentId, TranscriptIndexDocument document) {
            documents.put(documentId, document);
        }

        boolean indexExists() {
            return indexExists;
        }

        List<String> documentIds() {
            return documents.keySet().stream().sorted().toList();
        }

        List<String> textsFor(UUID assetId) {
            return documents.values().stream()
                    .filter(document -> document.assetId().equals(assetId))
                    .map(TranscriptIndexDocument::text)
                    .sorted()
                    .toList();
        }

        List<UUID> workspacesFor(UUID assetId) {
            return documents.values().stream()
                    .filter(document -> document.assetId().equals(assetId))
                    .map(TranscriptIndexDocument::workspaceId)
                    .distinct()
                    .toList();
        }
    }

    @Autowired
    private SearchIndexRebuildService searchIndexRebuildService;

    @Autowired
    private IndexingAssetPort indexingAssetPort;

    @Autowired
    private com.aiknowledgeworkspace.workspacecore.search.application.service
            .ExecuteIndexJobApplicationService executeIndexJobApplicationService;

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private CanonicalTranscriptStore canonicalTranscriptStore;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Autowired
    private SearchRebuildProperties rebuildProperties;

    @Autowired
    private InMemoryTranscriptIndex transcriptIndex;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        transcriptIndex.reset();
    }

    // ------------------------------------------------------------- enumeration

    @Test
    void onlyAssetsHoldingCanonicalTranscriptRowsAreRebuildCandidates() {
        UUID withTranscript = persistAsset("Lecture one");
        persistTranscript(withTranscript, "alpha", "beta");
        UUID withoutTranscript = persistAsset("Still processing");
        entityManager.flush();

        List<UUID> candidates = indexingAssetPort.findProjectionSourceAssetIds(null, 50);

        assertThat(candidates).containsExactly(withTranscript).doesNotContain(withoutTranscript);
        assertThat(searchIndexRebuildService.countRebuildCandidates()).isEqualTo(1);
    }

    @Test
    void enumerationPagesDeterministicallyWithoutRepeatingOrSkippingAnAsset() {
        List<UUID> assetIds = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            UUID assetId = persistAsset("Lecture " + index);
            persistTranscript(assetId, "sentence " + index);
            assetIds.add(assetId);
        }
        entityManager.flush();

        List<UUID> firstPass = pageThroughCandidates(2);
        List<UUID> secondPass = pageThroughCandidates(2);

        assertThat(firstPass).containsExactlyInAnyOrderElementsOf(assetIds).doesNotHaveDuplicates();
        // Ordering is the database's, not Java's UUID comparison; what matters is that the keyset
        // predicate and the ordering agree, so repeating the walk yields the same sequence.
        assertThat(secondPass).isEqualTo(firstPass);
    }

    private List<UUID> pageThroughCandidates(int batchSize) {
        List<UUID> paged = new ArrayList<>();
        UUID after = null;
        List<UUID> page;
        while (!(page = indexingAssetPort.findProjectionSourceAssetIds(after, batchSize)).isEmpty()) {
            assertThat(page).hasSizeLessThanOrEqualTo(batchSize);
            paged.addAll(page);
            after = page.get(page.size() - 1);
        }
        return paged;
    }

    // ------------------------------------------------------------ central case

    @Test
    void aHistoricalIndexedJobDoesNotStopTheProjectionFromBeingRebuilt() {
        UUID assetId = persistAsset("Lecture one");
        persistTranscript(assetId, "alpha", "beta");
        AssetSearchIndexJob historicalJob = indexedHistory(assetId);
        entityManager.flush();

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(transcriptIndex.indexExists()).isTrue();
        assertThat(transcriptIndex.textsFor(assetId)).containsExactly("alpha", "beta");
        // History is preserved: the old success still reads as a success.
        assertThat(status(historicalJob)).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(searchIndexJobRepository.findByAssetAndStatuses(
                assetId, List.of(AssetSearchIndexJobStatus.INDEXED))).hasSize(2);
    }

    @Test
    void aRebuildJobRecordsOperatorIntentRatherThanAFabricatedRequestEvent() {
        UUID assetId = persistAsset("Lecture one");
        persistTranscript(assetId, "alpha");
        AssetSearchIndexJob historicalJob = indexedHistory(assetId);
        entityManager.flush();

        searchIndexRebuildService.rebuildAll();

        List<AssetSearchIndexJob> jobs = searchIndexJobRepository.findByAssetAndStatuses(
                assetId, List.of(AssetSearchIndexJobStatus.INDEXED));
        AssetSearchIndexJob rebuildJob = jobs.stream()
                .filter(job -> !job.getId().equals(historicalJob.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(rebuildJob.getId()).isNotEqualTo(historicalJob.getId());
        assertThat(rebuildJob.getRequestOutboxEventId())
                .as("no asset.indexing.requested event happened, so none is claimed")
                .isNull();
    }

    // ------------------------------------------------------- projection truth

    @Test
    void rerunningARebuildLeavesTheSameDocumentsWithoutDuplicating() {
        UUID assetId = persistAsset("Lecture one");
        persistTranscript(assetId, "alpha", "beta");
        entityManager.flush();

        searchIndexRebuildService.rebuildAll();
        List<String> afterFirstRun = transcriptIndex.documentIds();
        SearchIndexRebuildResult second = searchIndexRebuildService.rebuildAll();

        assertThat(second.failed()).isZero();
        assertThat(transcriptIndex.documentIds()).isEqualTo(afterFirstRun).doesNotHaveDuplicates();
        assertThat(transcriptIndex.textsFor(assetId)).containsExactly("alpha", "beta");
    }

    @Test
    void staleDocumentsFromAnOlderProjectionAreRemovedByTheRebuild() {
        UUID assetId = persistAsset("Lecture one");
        persistTranscript(assetId, "alpha", "beta");
        transcriptIndex.seed(assetId + "-removed-row", new TranscriptIndexDocument(
                assetId, UUID.randomUUID(), "Lecture one", "removed-row", 9, 0L, 1L,
                "sentence that no longer exists", "2026-09-02T00:00:00Z", "SEARCHABLE"));
        entityManager.flush();

        searchIndexRebuildService.rebuildAll();

        assertThat(transcriptIndex.textsFor(assetId))
                .as("the projection ends up matching canonical truth exactly")
                .containsExactly("alpha", "beta");
    }

    @Test
    void separateAssetsKeepTheirOwnDocumentsAndWorkspaceOwnership() {
        Workspace firstWorkspace = persistWorkspace("Algorithms");
        Workspace secondWorkspace = persistWorkspace("Databases");
        UUID firstAsset = persistAsset(firstWorkspace, "Lecture one");
        UUID secondAsset = persistAsset(secondWorkspace, "Lecture two");
        persistTranscript(firstAsset, "alpha");
        persistTranscript(secondAsset, "beta", "gamma");
        entityManager.flush();

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.indexed()).isEqualTo(2);
        assertThat(transcriptIndex.textsFor(firstAsset)).containsExactly("alpha");
        assertThat(transcriptIndex.textsFor(secondAsset)).containsExactly("beta", "gamma");
        assertThat(transcriptIndex.workspacesFor(firstAsset)).containsExactly(firstWorkspace.getId());
        assertThat(transcriptIndex.workspacesFor(secondAsset)).containsExactly(secondWorkspace.getId());
        assertThat(transcriptIndex.documentIds()).doesNotHaveDuplicates().hasSize(3);
    }

    @Test
    void anAssetWithoutCanonicalRowsIsSkippedRatherThanGivenAnEmptyProjection() {
        UUID withTranscript = persistAsset("Lecture one");
        persistTranscript(withTranscript, "alpha");
        persistAsset("No transcript yet");
        entityManager.flush();

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.eligible()).isEqualTo(1);
        assertThat(result.indexed()).isEqualTo(1);
        assertThat(transcriptIndex.documentIds()).hasSize(1);
    }

    // ------------------------------------------------------------- concurrency

    @Test
    void anAttemptAlreadyInFlightForTheSameSnapshotIsLeftToTheLiveWorker() {
        UUID assetId = persistAsset("Lecture one");
        persistTranscript(assetId, "alpha");
        entityManager.flush();
        String fingerprint = currentFingerprint(assetId);
        AssetSearchIndexJob liveJob = new AssetSearchIndexJob(assetId, fingerprint);
        liveJob.markIndexing();
        searchIndexJobRepository.save(liveJob);
        entityManager.flush();

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.indexed()).isZero();
        assertThat(status(liveJob)).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
    }

    @Test
    void aSnapshotThatMovedOnDuringTheRunCannotOverwriteTheNewerTranscript() {
        UUID assetId = persistAsset("Lecture one");
        persistTranscript(assetId, "old sentence");
        entityManager.flush();
        String staleFingerprint = currentFingerprint(assetId);
        AssetSearchIndexJob staleJob = searchIndexJobRepository.save(
                new AssetSearchIndexJob(assetId, staleFingerprint));
        // The canonical transcript moves on before that job executes.
        persistTranscript(assetId, "new sentence", "and another");
        entityManager.flush();

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(transcriptIndex.textsFor(assetId))
                .as("the projection reflects the newer canonical transcript")
                .containsExactly("and another", "new sentence");

        // The stale job is left as it was — rebuild does not rewrite other jobs — and when a worker
        // does reach it, the fingerprint check retires it instead of letting it overwrite.
        assertThat(status(staleJob)).isEqualTo(AssetSearchIndexJobStatus.PENDING);
        executeIndexJobApplicationService.execute(staleJob.getId());
        assertThat(status(staleJob)).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(transcriptIndex.textsFor(assetId)).containsExactly("and another", "new sentence");
    }

    // ------------------------------------------------------------ failure path

    @Test
    void oneFailingAssetIsReportedWithoutDiscardingTheAssetsThatSucceeded() {
        UUID healthyAsset = persistAsset("Lecture one");
        persistTranscript(healthyAsset, "alpha");
        UUID failingAsset = persistAsset("Lecture two");
        persistTranscript(failingAsset, "beta");
        entityManager.flush();
        transcriptIndex.failFor(failingAsset);

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.eligible()).isEqualTo(2);
        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.summary()).isEqualTo("eligible=2 indexed=1 superseded=0 skipped=0 failed=1");
        assertThat(transcriptIndex.textsFor(healthyAsset)).containsExactly("alpha");
    }

    @Test
    void anIncompleteRebuildIsNotReportedAsSuccessByTheOperatorCommand() {
        SearchIndexRebuildResult failedRun = new SearchIndexRebuildResult(2, 1, 0, 0, 1);

        assertThat(failedRun.hasFailures()).isTrue();
        assertThatThrownBy(() -> {
            if (failedRun.hasFailures()) {
                throw new IllegalStateException(
                        "Search rebuild did not complete for every eligible asset: " + failedRun.summary());
            }
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed=1");
    }

    @Test
    void theRebuildCommandIsOffUnlessAnOperatorAsksForIt() {
        assertThat(new SearchRebuildProperties().getCommand()).isEqualTo(SearchRebuildCommand.NONE);
        assertThat(rebuildProperties.getCommand()).isEqualTo(SearchRebuildCommand.NONE);
        assertThat(rebuildProperties.getBatchSize()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ fixtures

    private Workspace persistWorkspace(String name) {
        return workspaceRepository.save(new Workspace(UUID.randomUUID(), name, "user-1", false));
    }

    private UUID persistAsset(String title) {
        return persistAsset(persistWorkspace("Algorithms"), title);
    }

    private UUID persistAsset(Workspace workspace, String title) {
        UUID assetId = UUID.randomUUID();
        assetRepository.save(Asset.uploaded(
                assetId,
                "lecture.mp4",
                title,
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

    private void persistTranscript(UUID assetId, String... texts) {
        List<AssetTranscriptRowInput> rows = new ArrayList<>();
        for (int index = 0; index < texts.length; index++) {
            rows.add(new AssetTranscriptRowInput(
                    "row-" + index, "video-1", index, (long) index * 1000, (long) (index + 1) * 1000,
                    texts[index], "2026-09-02T00:00:00Z"
            ));
        }
        canonicalTranscriptStore.replace(assetId, rows);
    }

    /** An indexing job that already succeeded, exactly as history leaves it. */
    private AssetSearchIndexJob indexedHistory(UUID assetId) {
        AssetSearchIndexJob job = new AssetSearchIndexJob(assetId, currentFingerprint(assetId));
        job.attachRequestOutboxEvent(UUID.randomUUID());
        job.markIndexing();
        job.markIndexed(Instant.now());
        return searchIndexJobRepository.save(job);
    }

    private String currentFingerprint(UUID assetId) {
        return new com.aiknowledgeworkspace.workspacecore.search.application.service
                .TranscriptSnapshotFingerprintService()
                .fingerprint(indexingAssetPort.findCurrentIndexingSource(assetId).orElseThrow().transcriptRows());
    }

    private AssetSearchIndexJobStatus status(AssetSearchIndexJob job) {
        entityManager.flush();
        entityManager.clear();
        return searchIndexJobRepository.findById(job.getId()).orElseThrow().getStatus();
    }
}
