package com.aiknowledgeworkspace.workspacecore.search.adapter.in.metrics;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.IndexingBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
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
 * Publishes search indexing pressure as gauges read from canonical PostgreSQL state, so the values
 * survive a process restart instead of resetting the way in-memory counters would.
 *
 * <p><strong>Stuck definition.</strong> A job is stuck when it is still {@code INDEXING} and was
 * claimed at least {@code workspace.search.indexing.stale-age} ago. Indexing runs inside one Kafka
 * message handling bounded by the Elasticsearch connect and read timeouts (3s and 10s by default),
 * so a job holding the claim for minutes means the worker died mid-attempt.
 *
 * <p>The threshold is read from the indexing configuration rather than kept here, so this gauge
 * counts exactly the jobs automatic recovery accepts: a value that persists across recovery
 * intervals is a wedge recovery could not clear, not an attempt in flight.
 *
 * <p>{@code INDEXED} and {@code SUPERSEDED} are excluded: they are terminal history that only grows.
 */
@Component
class SearchIndexBacklogMetrics {

    private static final Duration DEFAULT_SNAPSHOT_TTL = Duration.ofSeconds(1);

    private final SearchIndexJobStore searchIndexJobRepository;
    private final SearchIndexingProperties indexingProperties;
    private final Clock clock;
    private final long snapshotTtlNanos;
    private final AtomicReference<CachedSnapshot> cachedSnapshot = new AtomicReference<>();

    @Autowired
    SearchIndexBacklogMetrics(
            MeterRegistry meterRegistry,
            SearchIndexJobStore searchIndexJobRepository,
            SearchIndexingProperties indexingProperties
    ) {
        this(meterRegistry, searchIndexJobRepository, indexingProperties, Clock.systemUTC(), DEFAULT_SNAPSHOT_TTL);
    }

    SearchIndexBacklogMetrics(
            MeterRegistry meterRegistry,
            SearchIndexJobStore searchIndexJobRepository,
            SearchIndexingProperties indexingProperties,
            Clock clock,
            Duration snapshotTtl
    ) {
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.indexingProperties = indexingProperties;
        this.clock = clock;
        this.snapshotTtlNanos = snapshotTtl.toNanos();
        registerMeters(meterRegistry);
    }

    private void registerMeters(MeterRegistry meterRegistry) {
        backlogGauge(meterRegistry, "pending", IndexingBacklogSnapshot::pending);
        backlogGauge(meterRegistry, "indexing", IndexingBacklogSnapshot::indexing);
        backlogGauge(meterRegistry, "failed", IndexingBacklogSnapshot::failed);

        Gauge.builder("project3.search.index.stuck", this, metrics -> metrics.snapshot().stuckIndexing())
                .tag("status", "indexing")
                .description("Indexing jobs holding an indexing claim beyond the stuck threshold")
                .register(meterRegistry);

        Gauge.builder("project3.search.index.stuck.age.oldest", this, SearchIndexBacklogMetrics::oldestStuckAgeSeconds)
                .tag("status", "indexing")
                .baseUnit("seconds")
                .description("Age of the oldest indexing job holding an indexing claim beyond the stuck threshold")
                .register(meterRegistry);
    }

    private void backlogGauge(
            MeterRegistry meterRegistry,
            String status,
            ToLongFunction<IndexingBacklogSnapshot> reading
    ) {
        Gauge.builder("project3.search.index.jobs", this, metrics -> reading.applyAsLong(metrics.snapshot()))
                .tag("status", status)
                .description("Search indexing jobs in an operationally actionable status")
                .register(meterRegistry);
    }

    private double oldestStuckAgeSeconds() {
        Instant now = Instant.now(clock);
        return snapshotAt(now).oldestStuckIndexingAge(now).toSeconds();
    }

    private IndexingBacklogSnapshot snapshot() {
        return snapshotAt(Instant.now(clock));
    }

    /** One query serves all five gauges here; see {@code OutboxBacklogMetrics} for the same shape. */
    private IndexingBacklogSnapshot snapshotAt(Instant now) {
        long readingNanos = System.nanoTime();
        CachedSnapshot cached = cachedSnapshot.get();
        if (cached != null && readingNanos - cached.takenAtNanos() < snapshotTtlNanos) {
            return cached.snapshot();
        }
        IndexingBacklogSnapshot fresh =
                searchIndexJobRepository.loadBacklogSnapshot(now.minus(indexingProperties.getStaleAge()));
        cachedSnapshot.set(new CachedSnapshot(readingNanos, fresh));
        return fresh;
    }

    private record CachedSnapshot(long takenAtNanos, IndexingBacklogSnapshot snapshot) {
    }
}
