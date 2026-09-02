package com.aiknowledgeworkspace.workspacecore.processing.application.port.out;

import java.time.Duration;
import java.time.Instant;

/**
 * One consistent reading of how much processing work Spring is waiting on.
 *
 * <p>{@code PENDING} is the only state Spring genuinely owns while work is outstanding: it is set
 * when the request is created or retried, and only the arriving result moves the job to
 * {@code SUCCEEDED} or {@code FAILED}. {@code RUNNING} exists in the enum but nothing in this
 * service ever assigns it — the processor's own progress is not Spring's to report.
 *
 * <p>{@code oldestPendingSince} is when the longest-waiting job entered {@code PENDING}, and is
 * {@code null} when nothing is waiting.
 */
public record ProcessingBacklogSnapshot(long pending, Instant oldestPendingSince) {

    public static ProcessingBacklogSnapshot empty() {
        return new ProcessingBacklogSnapshot(0, null);
    }

    /** How long the longest-waiting job has waited at {@code now}, or zero when nothing waits. */
    public Duration oldestPendingWait(Instant now) {
        if (oldestPendingSince == null) {
            return Duration.ZERO;
        }
        Duration wait = Duration.between(oldestPendingSince, now);
        return wait.isNegative() ? Duration.ZERO : wait;
    }
}
