package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.adapter.in.scheduling.StaleIndexingRecoveryScheduler;
import com.aiknowledgeworkspace.workspacecore.search.application.result.IndexingRecoveryResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.StaleIndexingRecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Scheduling behaviour only: the tick is driven directly rather than waited for. Eligibility and
 * replay semantics live beside the recovery service.
 */
@ExtendWith(OutputCaptureExtension.class)
class StaleIndexingRecoverySchedulerTest {

    private final StaleIndexingRecoveryService recoveryService = mock(StaleIndexingRecoveryService.class);
    private final StaleIndexingRecoveryScheduler scheduler = new StaleIndexingRecoveryScheduler(recoveryService);

    @Test
    void eachTickDelegatesToTheRecoveryService() {
        when(recoveryService.recoverStaleIndexingJobs()).thenReturn(new IndexingRecoveryResult(0, 0, 0, 0, false));

        scheduler.recoverStaleIndexingJobsOnSchedule();

        verify(recoveryService).recoverStaleIndexingJobs();
    }

    @Test
    void recoveredJobsAreReportedAsCountsWithoutListingIdentifiers(CapturedOutput output) {
        when(recoveryService.recoverStaleIndexingJobs()).thenReturn(new IndexingRecoveryResult(4, 2, 1, 1, false));

        scheduler.recoverStaleIndexingJobsOnSchedule();

        assertThat(output.getAll())
                .contains("Stale indexing recovery completed eligible=4 recovered=2 skipped=1 failed=1");
    }

    @Test
    void aQuietTickSaysNothing(CapturedOutput output) {
        when(recoveryService.recoverStaleIndexingJobs()).thenReturn(new IndexingRecoveryResult(0, 0, 0, 0, false));

        scheduler.recoverStaleIndexingJobsOnSchedule();

        assertThat(output.getAll()).doesNotContain("Stale indexing recovery completed");
    }

    @Test
    void aFailedPassIsLoggedWithoutEscapingSoTheNextTickCanRetry(CapturedOutput output) {
        when(recoveryService.recoverStaleIndexingJobs())
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        assertThatCode(scheduler::recoverStaleIndexingJobsOnSchedule).doesNotThrowAnyException();

        assertThat(output.getAll())
                .contains("Stale indexing recovery failed category=IllegalStateException")
                .doesNotContain("connection pool exhausted");
    }
}
