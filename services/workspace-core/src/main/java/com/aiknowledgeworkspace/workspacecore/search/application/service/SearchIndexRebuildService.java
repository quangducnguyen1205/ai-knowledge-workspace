package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchRebuildProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.result.SearchIndexRebuildResult;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reconstructs the Elasticsearch projection from canonical PostgreSQL state.
 *
 * <p>This is not stale-job recovery. That finishes one attempt a crash interrupted; this one starts
 * from the premise that the projection itself is gone or wrong, walks canonical truth, and rebuilds
 * it. The obstacle it exists to solve: after the index is lost the newest job for an asset usually
 * still says {@code INDEXED}, so every normal path concludes there is nothing to do.
 *
 * <p><strong>History is preserved.</strong> Old jobs are never edited back into an active state —
 * their success was real, and rewriting it would lose the record of when the asset was first
 * indexed. A rebuild instead records new intent: a fresh job per asset carrying the fingerprint of
 * the transcript as it stands now. That job has no request outbox event id, because no
 * {@code asset.indexing.requested} event occurred — an operator asked for this, not the pipeline.
 *
 * <p><strong>No Kafka.</strong> The projection is rebuilt by invoking the canonical execution path
 * directly. Recovery must not depend on infrastructure beyond the two stores it is reconciling, and
 * routing through the outbox would also fabricate an event that never happened. Document
 * construction, the fingerprint check, the Elasticsearch write and finalisation all stay in
 * {@link ExecuteIndexJobApplicationService}: there remains exactly one implementation of indexing.
 */
@Service
public class SearchIndexRebuildService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexRebuildService.class);

    private final IndexingAssetPort indexingAssetPort;
    private final SearchIndexJobStore searchIndexJobStore;
    private final ExecuteIndexJobApplicationService executeIndexJobApplicationService;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final SearchRebuildProperties properties;

    public SearchIndexRebuildService(
            IndexingAssetPort indexingAssetPort,
            SearchIndexJobStore searchIndexJobStore,
            ExecuteIndexJobApplicationService executeIndexJobApplicationService,
            TranscriptSnapshotFingerprintService fingerprintService,
            SearchRebuildProperties properties
    ) {
        this.indexingAssetPort = indexingAssetPort;
        this.searchIndexJobStore = searchIndexJobStore;
        this.executeIndexJobApplicationService = executeIndexJobApplicationService;
        this.fingerprintService = fingerprintService;
        this.properties = properties;
    }

    /** Counts the assets a rebuild would touch, without writing anything. */
    public int countRebuildCandidates() {
        int candidates = 0;
        UUID afterAssetId = null;
        List<UUID> page;
        while (!(page = nextPage(afterAssetId)).isEmpty()) {
            candidates += page.size();
            afterAssetId = page.get(page.size() - 1);
        }
        return candidates;
    }

    public SearchIndexRebuildResult rebuildAll() {
        LOGGER.info("Search rebuild started batchSize={}", properties.getBatchSize());
        int eligible = 0;
        int indexed = 0;
        int superseded = 0;
        int skipped = 0;
        int failed = 0;

        UUID afterAssetId = null;
        List<UUID> page;
        while (!(page = nextPage(afterAssetId)).isEmpty()) {
            for (UUID assetId : page) {
                eligible++;
                switch (rebuildAsset(assetId)) {
                    case INDEXED -> indexed++;
                    case SUPERSEDED -> superseded++;
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                }
            }
            afterAssetId = page.get(page.size() - 1);
            LOGGER.info(
                    "Search rebuild batch completed eligible={} indexed={} superseded={} skipped={} failed={}",
                    eligible, indexed, superseded, skipped, failed
            );
        }

        SearchIndexRebuildResult result = new SearchIndexRebuildResult(eligible, indexed, superseded, skipped, failed);
        LOGGER.info("Search rebuild completed {}", result.summary());
        return result;
    }

    private List<UUID> nextPage(UUID afterAssetId) {
        return indexingAssetPort.findProjectionSourceAssetIds(afterAssetId, properties.getBatchSize());
    }

    private AssetOutcome rebuildAsset(UUID assetId) {
        try {
            Optional<IndexingAssetSource> source = indexingAssetPort.findCurrentIndexingSource(assetId);
            if (source.isEmpty() || source.get().transcriptRows().isEmpty()) {
                // Nothing canonical to project. Normal indexing would produce no documents either,
                // so a rebuild must not invent an empty searchable asset.
                return AssetOutcome.SKIPPED;
            }

            String fingerprint = fingerprintService.fingerprint(source.get().transcriptRows());
            if (!searchIndexJobStore.findByAssetFingerprintAndStatuses(
                    assetId,
                    fingerprint,
                    List.of(AssetSearchIndexJobStatus.PENDING, AssetSearchIndexJobStatus.INDEXING)
            ).isEmpty()) {
                // Live indexing already owns this exact snapshot; it writes the same documents.
                return AssetOutcome.SKIPPED;
            }

            AssetSearchIndexJob rebuildJob = searchIndexJobStore.save(
                    new AssetSearchIndexJob(assetId, fingerprint));
            AssetSearchIndexJobStatus status =
                    executeIndexJobApplicationService.execute(rebuildJob.getId()).status();
            return switch (status) {
                case INDEXED -> AssetOutcome.INDEXED;
                case SUPERSEDED -> AssetOutcome.SUPERSEDED;
                default -> AssetOutcome.FAILED;
            };
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Search rebuild failed for one asset assetId={} failureCategory={}",
                    assetId,
                    exception.getClass().getSimpleName()
            );
            return AssetOutcome.FAILED;
        }
    }

    private enum AssetOutcome {
        INDEXED,
        SUPERSEDED,
        SKIPPED,
        FAILED
    }
}
