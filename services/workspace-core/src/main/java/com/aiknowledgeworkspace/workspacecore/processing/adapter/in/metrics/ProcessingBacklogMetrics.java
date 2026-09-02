package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.metrics;

import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publishes how much processing work is outstanding, read from canonical PostgreSQL state.
 *
 * <p>Deliberately no stuck gauge. A {@code PENDING} job is waiting for a result from the external
 * processor, and Spring cannot tell a legitimately long transcription from a dead worker: the
 * transcript-ready event is the only signal it gets, and no lease or heartbeat backs it. Reporting
 * a wedge here would be inventing a fact the model does not carry, so this exposes backlog and the
 * longest wait — genuine pressure — and leaves the wedge classification to a task that can consult
 * the processor.
 */
@Component
class ProcessingBacklogMetrics {

    private static final Duration DEFAULT_SNAPSHOT_TTL = Duration.ofSeconds(1);

    private final ProcessingJobStore processingJobRepository;
    private final Clock clock;
    private final long snapshotTtlNanos;
    private final AtomicReference<CachedSnapshot> cachedSnapshot = new AtomicReference<>();

    @Autowired
    ProcessingBacklogMetrics(MeterRegistry meterRegistry, ProcessingJobStore processingJobRepository) {
        this(meterRegistry, processingJobRepository, Clock.systemUTC(), DEFAULT_SNAPSHOT_TTL);
    }

    ProcessingBacklogMetrics(
            MeterRegistry meterRegistry,
            ProcessingJobStore processingJobRepository,
            Clock clock,
            Duration snapshotTtl
    ) {
        this.processingJobRepository = processingJobRepository;
        this.clock = clock;
        this.snapshotTtlNanos = snapshotTtl.toNanos();
        registerMeters(meterRegistry);
    }

    private void registerMeters(MeterRegistry meterRegistry) {
        Gauge.builder("project3.processing.jobs", this, metrics -> metrics.snapshot().pending())
                .tag("status", "pending")
                .description("Processing jobs still waiting for a result from the processor")
                .register(meterRegistry);

        Gauge.builder("project3.processing.wait.age.oldest", this, ProcessingBacklogMetrics::oldestWaitSeconds)
                .tag("status", "pending")
                .baseUnit("seconds")
                .description("How long the longest-waiting processing job has waited for its result")
                .register(meterRegistry);
    }

    private double oldestWaitSeconds() {
        Instant now = Instant.now(clock);
        return snapshotAt(now).oldestPendingWait(now).toSeconds();
    }

    private ProcessingBacklogSnapshot snapshot() {
        return snapshotAt(Instant.now(clock));
    }

    private ProcessingBacklogSnapshot snapshotAt(Instant now) {
        long readingNanos = System.nanoTime();
        CachedSnapshot cached = cachedSnapshot.get();
        if (cached != null && readingNanos - cached.takenAtNanos() < snapshotTtlNanos) {
            return cached.snapshot();
        }
        ProcessingBacklogSnapshot fresh = processingJobRepository.loadBacklogSnapshot();
        cachedSnapshot.set(new CachedSnapshot(readingNanos, fresh));
        return fresh;
    }

    private record CachedSnapshot(long takenAtNanos, ProcessingBacklogSnapshot snapshot) {
    }
}
