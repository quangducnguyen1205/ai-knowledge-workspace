package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.result.IndexingRecoveryResult;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Finishes indexing jobs whose worker died mid-attempt.
 *
 * <p>The Kafka record that started the attempt cannot be relied on to come back. It is acknowledged
 * only on a terminal outcome, so a crash normally does leave it uncommitted and redelivery retries
 * the job — but a rejected event is acknowledged while the job is still claimed, a record skipped by
 * the container's error handler after its retries is committed the same way, and offsets reset or
 * topic retention can drop it entirely. When any of those happen the job keeps its claim forever and
 * the asset never becomes searchable. This scheduler is the trigger of last resort.
 *
 * <p>It is only a trigger: the replay runs through {@link ExecuteIndexJobApplicationService}, the
 * same path the listener uses, so the canonical read of the transcript, the fingerprint check, the
 * Elasticsearch write and the finalisation all keep exactly one implementation.
 *
 * <p><strong>Replay is safe because the write is a replacement, not an append.</strong> An attempt
 * deletes every document for the asset and then bulk-indexes the full projection under document ids
 * derived from the asset and row, so running it again — whether the previous attempt wrote nothing,
 * part of the set, or all of it — converges on the same documents. A job whose transcript has since
 * changed is superseded by the fingerprint check rather than allowed to overwrite newer content.
 */
@Service
public class StaleIndexingRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaleIndexingRecoveryService.class);

    private final SearchIndexJobStore searchIndexJobStore;
    private final ExecuteIndexJobApplicationService executeIndexJobApplicationService;
    private final SearchIndexingProperties properties;
    private final Clock clock;

    @Autowired
    public StaleIndexingRecoveryService(
            SearchIndexJobStore searchIndexJobStore,
            ExecuteIndexJobApplicationService executeIndexJobApplicationService,
            SearchIndexingProperties properties
    ) {
        this(searchIndexJobStore, executeIndexJobApplicationService, properties, Clock.systemUTC());
    }

    public StaleIndexingRecoveryService(
            SearchIndexJobStore searchIndexJobStore,
            ExecuteIndexJobApplicationService executeIndexJobApplicationService,
            SearchIndexingProperties properties,
            Clock clock
    ) {
        this.searchIndexJobStore = searchIndexJobStore;
        this.executeIndexJobApplicationService = executeIndexJobApplicationService;
        this.properties = properties;
        this.clock = clock;
    }

    public IndexingRecoveryResult recoverStaleIndexingJobs() {
        if (!properties.isRecoveryEnabled()) {
            return IndexingRecoveryResult.disabledResult();
        }

        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(properties.getStaleAge());
        List<UUID> staleJobIds = searchIndexJobStore.findStaleIndexingIds(
                AssetSearchIndexJobStatus.INDEXING,
                cutoff,
                properties.getRecoveryBatchSize()
        );

        int recovered = 0;
        int skipped = 0;
        int failed = 0;
        for (UUID indexingJobId : staleJobIds) {
            // The conditional claim decides, not this loop: a job another worker took, or one that
            // finished between the scan and here, no longer matches and is left alone.
            if (searchIndexJobStore.claimStaleIndexingJob(
                    indexingJobId, AssetSearchIndexJobStatus.INDEXING, cutoff, now) != 1) {
                skipped++;
                continue;
            }
            if (replay(indexingJobId)) {
                recovered++;
            } else {
                failed++;
            }
        }

        return new IndexingRecoveryResult(staleJobIds.size(), recovered, skipped, failed, false);
    }

    private boolean replay(UUID indexingJobId) {
        try {
            executeIndexJobApplicationService.execute(indexingJobId);
            return true;
        } catch (RuntimeException exception) {
            // The job keeps its claim and ages back into the stale window, so a later pass retries
            // it once the cause clears. The attempt's own failure detail is already recorded.
            LOGGER.warn(
                    "Stale indexing replay failed indexingJobId={} failureCategory={}",
                    indexingJobId,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }
}
