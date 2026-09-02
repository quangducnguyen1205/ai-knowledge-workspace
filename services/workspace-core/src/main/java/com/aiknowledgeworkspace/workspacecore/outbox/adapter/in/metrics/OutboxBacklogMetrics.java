package com.aiknowledgeworkspace.workspacecore.outbox.adapter.in.metrics;

import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox publication pressure as gauges read from canonical PostgreSQL state, so the
 * values survive a process restart instead of resetting the way in-memory counters would.
 *
 * <p><strong>Stuck definition.</strong> An event is stuck when it is still {@code PUBLISHING} and
 * was claimed at least {@code outbox.recovery.stale-publishing-age} ago. The relay claims an event,
 * publishes it within the Kafka send timeout (10s by default), and immediately moves it on, so an
 * event that has held the claim for minutes means the claiming process died between the claim and
 * the publish.
 *
 * <p>The threshold is read from the recovery configuration rather than kept here, so this gauge
 * counts exactly the rows automatic recovery accepts: a non-zero value that persists across
 * recovery runs is a wedge recovery could not clear, not merely a claim in flight.
 *
 * <p>Only actionable statuses are exposed. {@code PUBLISHED} rows are retained history and would
 * report table growth rather than work still waiting.
 */
@Component
class OutboxBacklogMetrics {

    private static final Duration DEFAULT_SNAPSHOT_TTL = Duration.ofSeconds(1);

    private final OutboxEventStore outboxEventRepository;
    private final OutboxRecoveryProperties recoveryProperties;
    private final Clock clock;
    private final long snapshotTtlNanos;
    private final AtomicReference<CachedSnapshot> cachedSnapshot = new AtomicReference<>();

    @Autowired
    OutboxBacklogMetrics(
            MeterRegistry meterRegistry,
            OutboxEventStore outboxEventRepository,
            OutboxRecoveryProperties recoveryProperties
    ) {
        this(meterRegistry, outboxEventRepository, recoveryProperties, Clock.systemUTC(), DEFAULT_SNAPSHOT_TTL);
    }

    OutboxBacklogMetrics(
            MeterRegistry meterRegistry,
            OutboxEventStore outboxEventRepository,
            OutboxRecoveryProperties recoveryProperties,
            Clock clock,
            Duration snapshotTtl
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.recoveryProperties = recoveryProperties;
        this.clock = clock;
        this.snapshotTtlNanos = snapshotTtl.toNanos();
        registerMeters(meterRegistry);
    }

    private void registerMeters(MeterRegistry meterRegistry) {
        backlogGauge(meterRegistry, "pending", OutboxBacklogSnapshot::pending);
        backlogGauge(meterRegistry, "publishing", OutboxBacklogSnapshot::publishing);
        backlogGauge(meterRegistry, "failed", OutboxBacklogSnapshot::failed);

        Gauge.builder("project3.outbox.stuck", this, metrics -> metrics.snapshot().stuckPublishing())
                .tag("status", "publishing")
                .description("Outbox events holding a publication claim beyond the stuck threshold")
                .register(meterRegistry);

        Gauge.builder("project3.outbox.stuck.age.oldest", this, OutboxBacklogMetrics::oldestStuckAgeSeconds)
                .tag("status", "publishing")
                .baseUnit("seconds")
                .description("Age of the oldest outbox event holding a publication claim beyond the stuck threshold")
                .register(meterRegistry);
    }

    private void backlogGauge(
            MeterRegistry meterRegistry,
            String status,
            ToLongFunction<OutboxBacklogSnapshot> reading
    ) {
        Gauge.builder("project3.outbox.events", this, metrics -> reading.applyAsLong(metrics.snapshot()))
                .tag("status", status)
                .description("Outbox events in an operationally actionable status")
                .register(meterRegistry);
    }

    private double oldestStuckAgeSeconds() {
        Instant now = Instant.now(clock);
        return snapshotAt(now).oldestStuckPublishingAge(now).toSeconds();
    }

    private OutboxBacklogSnapshot snapshot() {
        return snapshotAt(Instant.now(clock));
    }

    /**
     * One query serves every meter here: a scrape reads five gauges, and without this the same
     * aggregate would run five times. The window is short enough that a scrape still sees the
     * state of that scrape.
     */
    private OutboxBacklogSnapshot snapshotAt(Instant now) {
        long readingNanos = System.nanoTime();
        CachedSnapshot cached = cachedSnapshot.get();
        if (cached != null && readingNanos - cached.takenAtNanos() < snapshotTtlNanos) {
            return cached.snapshot();
        }
        OutboxBacklogSnapshot fresh = outboxEventRepository.loadBacklogSnapshot(
                now.minus(recoveryProperties.getStalePublishingAge()));
        cachedSnapshot.set(new CachedSnapshot(readingNanos, fresh));
        return fresh;
    }

    private record CachedSnapshot(long takenAtNanos, OutboxBacklogSnapshot snapshot) {
    }
}
