package com.aiknowledgeworkspace.workspacecore.outbox.application.port.out;

import java.time.Duration;
import java.time.Instant;

/**
 * One consistent reading of outbox publication pressure, taken by a single query so every meter
 * derived from it agrees with the others.
 *
 * <p>Only operationally actionable statuses are counted. {@code PUBLISHED} is excluded on purpose:
 * published rows are retained history, so counting them would report lifetime table size rather
 * than work that still needs to happen.
 *
 * <p>{@code oldestStuckPublishingSince} is the claim timestamp of the oldest event that already
 * satisfies the stuck condition, and is {@code null} when nothing is stuck.
 */
public record OutboxBacklogSnapshot(
        long pending,
        long publishing,
        long failed,
        long stuckPublishing,
        Instant oldestStuckPublishingSince
) {

    public static OutboxBacklogSnapshot empty() {
        return new OutboxBacklogSnapshot(0, 0, 0, 0, null);
    }

    /** Age of the oldest stuck event at {@code now}, or zero when nothing is stuck. */
    public Duration oldestStuckPublishingAge(Instant now) {
        if (oldestStuckPublishingSince == null) {
            return Duration.ZERO;
        }
        Duration age = Duration.between(oldestStuckPublishingSince, now);
        return age.isNegative() ? Duration.ZERO : age;
    }
}
