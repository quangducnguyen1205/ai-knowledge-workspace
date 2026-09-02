package com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing;

import java.time.Duration;
import java.time.Instant;

/**
 * One consistent reading of search indexing pressure, taken by a single query so every meter
 * derived from it agrees with the others.
 *
 * <p>{@code INDEXED} and {@code SUPERSEDED} are excluded on purpose: both are terminal history that
 * only accumulates, so counting them would report how much indexing has ever happened rather than
 * what still needs to happen.
 *
 * <p>{@code oldestStuckIndexingSince} is the claim timestamp of the oldest job that already
 * satisfies the stuck condition, and is {@code null} when nothing is stuck.
 */
public record IndexingBacklogSnapshot(
        long pending,
        long indexing,
        long failed,
        long stuckIndexing,
        Instant oldestStuckIndexingSince
) {

    public static IndexingBacklogSnapshot empty() {
        return new IndexingBacklogSnapshot(0, 0, 0, 0, null);
    }

    /** Age of the oldest stuck job at {@code now}, or zero when nothing is stuck. */
    public Duration oldestStuckIndexingAge(Instant now) {
        if (oldestStuckIndexingSince == null) {
            return Duration.ZERO;
        }
        Duration age = Duration.between(oldestStuckIndexingSince, now);
        return age.isNegative() ? Duration.ZERO : age;
    }
}
