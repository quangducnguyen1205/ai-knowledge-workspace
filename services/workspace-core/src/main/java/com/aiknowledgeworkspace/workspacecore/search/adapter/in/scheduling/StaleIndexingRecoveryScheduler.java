package com.aiknowledgeworkspace.workspacecore.search.adapter.in.scheduling;

import com.aiknowledgeworkspace.workspacecore.search.application.result.IndexingRecoveryResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.StaleIndexingRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Periodic trigger for indexing jobs abandoned mid-attempt. Kept separate from the request relay:
 * that one moves outbox rows to Kafka on a ten-second cadence and costs a database read, while this
 * one replays work against Elasticsearch and only ever acts on claims that are minutes old.
 */
@Component
@ConditionalOnProperty(prefix = "workspace.search.indexing", name = "recovery-enabled", havingValue = "true")
public class StaleIndexingRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaleIndexingRecoveryScheduler.class);

    private final StaleIndexingRecoveryService staleIndexingRecoveryService;

    public StaleIndexingRecoveryScheduler(StaleIndexingRecoveryService staleIndexingRecoveryService) {
        this.staleIndexingRecoveryService = staleIndexingRecoveryService;
    }

    public void recoverStaleIndexingJobsOnSchedule() {
        try {
            IndexingRecoveryResult result = staleIndexingRecoveryService.recoverStaleIndexingJobs();
            if (result.eligible() > 0) {
                // Counts only; each replayed job identifies itself through the indexing lifecycle
                // logs the canonical path already writes.
                LOGGER.info(
                        "Stale indexing recovery completed eligible={} recovered={} skipped={} failed={}",
                        result.eligible(),
                        result.recovered(),
                        result.skipped(),
                        result.failed()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Stale indexing recovery failed category={}", exception.getClass().getSimpleName());
        }
    }
}
