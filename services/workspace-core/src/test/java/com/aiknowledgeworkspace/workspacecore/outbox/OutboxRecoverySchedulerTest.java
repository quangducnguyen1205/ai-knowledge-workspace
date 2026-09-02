package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.adapter.in.scheduling.OutboxRecoveryScheduler;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxFailureRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRecoveryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Scheduling behaviour only: the tick is driven directly rather than waited for, so nothing here
 * depends on wall-clock timing. Eligibility and state semantics live beside the recovery service.
 */
@ExtendWith(OutputCaptureExtension.class)
class OutboxRecoverySchedulerTest {

    private final OutboxFailureRecovery recoveryService = mock(OutboxFailureRecovery.class);
    private final OutboxRecoveryScheduler scheduler = new OutboxRecoveryScheduler(recoveryService);

    @Test
    void eachTickRunsBothTheFailurePassAndTheStaleClaimPass() {
        when(recoveryService.reconcileEligibleFailures()).thenReturn(new OutboxRecoveryResult(0, 0, 0, false));
        when(recoveryService.recoverStalePublishing()).thenReturn(new OutboxRecoveryResult(0, 0, 0, false));

        scheduler.reconcileOnSchedule();

        verify(recoveryService).reconcileEligibleFailures();
        verify(recoveryService).recoverStalePublishing();
    }

    @Test
    void recoveredStaleClaimsAreReportedAsCountsWithoutListingEventIds(CapturedOutput output) {
        when(recoveryService.reconcileEligibleFailures()).thenReturn(new OutboxRecoveryResult(0, 0, 0, false));
        when(recoveryService.recoverStalePublishing()).thenReturn(new OutboxRecoveryResult(3, 2, 1, false));

        scheduler.reconcileOnSchedule();

        assertThat(output.getAll())
                .contains("Stale outbox publishing recovery completed eligible=3 requeued=2 skipped=1");
    }

    @Test
    void aQuietTickSaysNothing(CapturedOutput output) {
        when(recoveryService.reconcileEligibleFailures()).thenReturn(new OutboxRecoveryResult(0, 0, 0, false));
        when(recoveryService.recoverStalePublishing()).thenReturn(new OutboxRecoveryResult(0, 0, 0, false));

        scheduler.reconcileOnSchedule();

        assertThat(output.getAll()).doesNotContain("Stale outbox publishing recovery completed");
    }

    @Test
    void aDatabaseFailureInOnePassIsReportedAndStillLetsTheOtherPassRun(CapturedOutput output) {
        when(recoveryService.reconcileEligibleFailures())
                .thenThrow(new IllegalStateException("connection pool exhausted"));
        when(recoveryService.recoverStalePublishing()).thenReturn(new OutboxRecoveryResult(1, 1, 0, false));

        assertThatCode(scheduler::reconcileOnSchedule).doesNotThrowAnyException();

        verify(recoveryService).recoverStalePublishing();
        assertThat(output.getAll())
                .contains("Outbox recovery reconciliation failed category=IllegalStateException")
                .doesNotContain("connection pool exhausted");
    }

    @Test
    void aStaleRecoveryFailureIsLoggedWithoutEscapingSoTheNextTickCanRetry(CapturedOutput output) {
        when(recoveryService.reconcileEligibleFailures()).thenReturn(new OutboxRecoveryResult(0, 0, 0, false));
        when(recoveryService.recoverStalePublishing())
                .thenThrow(new IllegalStateException("could not obtain lock"));

        assertThatCode(scheduler::reconcileOnSchedule).doesNotThrowAnyException();

        assertThat(output.getAll())
                .contains("Stale outbox publishing recovery failed category=IllegalStateException")
                .doesNotContain("could not obtain lock");
    }
}
