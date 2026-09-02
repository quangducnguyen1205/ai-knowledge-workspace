package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registry-level contract for the async backlog gauges: the meters an operator can rely on, the
 * tags they may carry, and one deterministic drill showing the values a wedged system produces.
 *
 * <p>Only the drill reads gauge values. The gauges share a one-second snapshot per subsystem so a
 * scrape of five meters costs one query, which also means a reading belongs to the moment it was
 * taken — arranging state and then reading once is how a scrape actually behaves.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-async-metrics;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class AsyncBacklogMetricsTest {

    private static final Pattern UUID_SHAPED = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /**
     * Identifier keys, not merely detailed ones: framework meters legitimately carry bounded
     * labels such as {@code error}, while anything here would grow a new time series per asset,
     * event, job, or person. The stricter rule for this service's own meters — exactly one
     * {@code status} tag — is asserted separately.
     */
    private static final List<String> FORBIDDEN_TAG_KEYS = List.of(
            "assetid", "eventid", "jobid", "workspaceid", "userid", "videoid",
            "correlationid", "causationid", "requesteventid", "indexingjobid",
            "snapshotfingerprint", "objectkey");

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private OutboxEventStore outboxEventRepository;

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private ProcessingJobStore processingJobRepository;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void everyBacklogAndStuckMeterIsRegisteredWithItsDocumentedTagAndUnit() {
        assertThat(backlogMeterNamesAndTags()).containsExactlyInAnyOrder(
                "project3.outbox.events{status=pending}",
                "project3.outbox.events{status=publishing}",
                "project3.outbox.events{status=failed}",
                "project3.outbox.stuck{status=publishing}",
                "project3.outbox.stuck.age.oldest{status=publishing}",
                "project3.search.index.jobs{status=pending}",
                "project3.search.index.jobs{status=indexing}",
                "project3.search.index.jobs{status=failed}",
                "project3.search.index.stuck{status=indexing}",
                "project3.search.index.stuck.age.oldest{status=indexing}",
                "project3.processing.jobs{status=pending}",
                "project3.processing.wait.age.oldest{status=pending}"
        );

        assertThat(meterRegistry.get("project3.outbox.stuck.age.oldest").gauge().getId().getBaseUnit())
                .isEqualTo("seconds");
        assertThat(meterRegistry.get("project3.search.index.stuck.age.oldest").gauge().getId().getBaseUnit())
                .isEqualTo("seconds");
        assertThat(meterRegistry.get("project3.processing.wait.age.oldest").gauge().getId().getBaseUnit())
                .isEqualTo("seconds");
    }

    @Test
    void noMeterCarriesAnIdentifierAsATag() {
        for (Meter meter : meterRegistry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                String key = tag.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace(".", "");
                assertThat(FORBIDDEN_TAG_KEYS)
                        .as("meter %s labels a series with identifier tag %s",
                                meter.getId().getName(), tag.getKey())
                        .doesNotContain(key);
                assertThat(UUID_SHAPED.matcher(tag.getValue()).find())
                        .as("meter %s tag %s carries an identifier value", meter.getId().getName(), tag.getKey())
                        .isFalse();
            }
        }
    }

    @Test
    void backlogMetersAreLabelledOnlyByABoundedStatusEnum() {
        Set<String> boundedStatusValues = Set.of("pending", "publishing", "failed", "indexing");

        meterRegistry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("project3."))
                .forEach(meter -> {
                    assertThat(meter.getId().getTags()).extracting(Tag::getKey).containsExactly("status");
                    assertThat(boundedStatusValues).contains(meter.getId().getTag("status"));
                });
    }

    @Test
    void operatorDrillReportsBacklogAndStuckStateForAWedgedSystem() {
        Instant now = Instant.now();
        UUID assetId = persistAsset();

        persistOutboxEvent();
        UUID wedgedEventId = persistOutboxEvent();
        claimForPublishing(wedgedEventId, now.minus(Duration.ofMinutes(42)));

        persistIndexJob(assetId, "fingerprint-waiting");
        AssetSearchIndexJob wedgedIndexJob = persistIndexJob(assetId, "fingerprint-wedged");
        wedgedIndexJob.markIndexing();
        searchIndexJobRepository.save(wedgedIndexJob);
        backdateUpdatedAt(AssetSearchIndexJob.class, wedgedIndexJob.getId(), now.minus(Duration.ofMinutes(30)));

        ProcessingJob waitingJob = persistProcessingJob(assetId);
        backdateUpdatedAt(ProcessingJob.class, waitingJob.getId(), now.minus(Duration.ofMinutes(20)));
        entityManager.flush();

        assertThat(gauge("project3.outbox.events", "pending")).isEqualTo(1.0);
        assertThat(gauge("project3.outbox.events", "publishing")).isEqualTo(1.0);
        assertThat(gauge("project3.outbox.events", "failed")).isZero();
        assertThat(gauge("project3.outbox.stuck", "publishing")).isEqualTo(1.0);
        assertThat(gauge("project3.outbox.stuck.age.oldest", "publishing"))
                .isGreaterThanOrEqualTo(Duration.ofMinutes(42).toSeconds());

        assertThat(gauge("project3.search.index.jobs", "pending")).isEqualTo(1.0);
        assertThat(gauge("project3.search.index.jobs", "indexing")).isEqualTo(1.0);
        assertThat(gauge("project3.search.index.jobs", "failed")).isZero();
        assertThat(gauge("project3.search.index.stuck", "indexing")).isEqualTo(1.0);
        assertThat(gauge("project3.search.index.stuck.age.oldest", "indexing"))
                .isGreaterThanOrEqualTo(Duration.ofMinutes(30).toSeconds());

        assertThat(gauge("project3.processing.jobs", "pending")).isEqualTo(1.0);
        assertThat(gauge("project3.processing.wait.age.oldest", "pending"))
                .isGreaterThanOrEqualTo(Duration.ofMinutes(20).toSeconds());
    }

    // ------------------------------------------------------------------ helpers

    private List<String> backlogMeterNamesAndTags() {
        return meterRegistry.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().startsWith("project3."))
                .map(id -> "%s{status=%s}".formatted(id.getName(), id.getTag("status")))
                .toList();
    }

    private double gauge(String name, String status) {
        return meterRegistry.get(name).tag("status", status).gauge().value();
    }

    private UUID persistOutboxEvent() {
        return outboxEventRepository.save(new OutboxEvent(
                UUID.randomUUID(),
                "asset.processing.requested",
                1,
                "ASSET",
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "{}"
        )).getId();
    }

    private void claimForPublishing(UUID eventId, Instant claimedAt) {
        outboxEventRepository.markPublishing(
                eventId,
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PUBLISHING,
                claimedAt
        );
    }

    private UUID persistAsset() {
        Workspace workspace = workspaceRepository.save(new Workspace(
                UUID.randomUUID(), "Algorithms", "user-1", false
        ));
        UUID assetId = UUID.randomUUID();
        assetRepository.save(Asset.uploaded(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.PROCESSING,
                workspace.getId(),
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        ));
        return assetId;
    }

    private AssetSearchIndexJob persistIndexJob(UUID assetId, String fingerprint) {
        return searchIndexJobRepository.save(new AssetSearchIndexJob(assetId, fingerprint));
    }

    private ProcessingJob persistProcessingJob(UUID assetId) {
        ProcessingJob job = new ProcessingJob(assetId, ProcessingJobStatus.PENDING, null);
        job.setProcessingRequestEventId(UUID.randomUUID());
        return processingJobRepository.save(job);
    }

    private void backdateUpdatedAt(Class<?> entityType, UUID id, Instant updatedAt) {
        entityManager.flush();
        entityManager.createQuery(
                        "update %s entity set entity.updatedAt = :updatedAt where entity.id = :id"
                                .formatted(entityType.getSimpleName()))
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
