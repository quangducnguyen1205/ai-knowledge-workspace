package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging.KafkaOutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRecoveryResult;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.application.service.OutboxRecoveryService;
import com.aiknowledgeworkspace.workspacecore.outbox.application.service.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxRecoveryOrigin;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crash-window recovery for events left claimed for publication.
 *
 * <p>The scenario throughout is a relay that claimed a row, moving it to {@code PUBLISHING}, and
 * then died. The row cannot say whether the broker had already accepted the event, so recovery
 * republishes and the system stays at-least-once: a possible duplicate under a stable event id,
 * never a silently dropped event.
 *
 * <p>The stale threshold here is deliberately not the production default, so a test that passes
 * proves the configured value is what both recovery and the stuck gauge actually read.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-stale-publishing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.enabled=true",
        "outbox.relay.batch-size=20",
        "outbox.relay.max-attempts=2",
        "outbox.relay.retry-delay=30s",
        "outbox.recovery.enabled=true",
        "outbox.recovery.interval=30s",
        "outbox.recovery.cooldown=60s",
        "outbox.recovery.stale-publishing-age=90s",
        "outbox.recovery.batch-size=50",
        "outbox.recovery.max-cycles=3",
        "workspace.kafka.enabled=true",
        "workspace.kafka.processing-requested-topic=asset.processing.requested.v1",
        "workspace.kafka.send-timeout=1s"
})
@Transactional
class StaleOutboxPublishingRecoveryTest {

    private static final Duration STALE_AFTER = Duration.ofSeconds(90);

    @Autowired
    private OutboxEventStore outboxEventRepository;

    @Autowired
    private OutboxRecoveryService outboxRecoveryService;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxMessagePublisher outboxMessagePublisher;

    @Autowired
    private OutboxRecoveryProperties recoveryProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private KafkaOutboxMessagePublisher.KafkaSender kafkaSender;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        reset(kafkaSender);
    }

    // ------------------------------------------------------------- eligibility

    @Test
    void aClaimYoungerThanTheThresholdIsLeftAloneForTheRelayThatHoldsIt() {
        UUID eventId = persistEvent();
        claimForPublishing(eventId, Instant.now().minusSeconds(10));

        OutboxRecoveryResult result = outboxRecoveryService.recoverStalePublishing();

        assertThat(result.eligible()).isZero();
        assertThat(result.requeued()).isZero();
        assertThat(status(eventId)).isEqualTo(OutboxEventStatus.PUBLISHING);
    }

    @Test
    void aClaimOlderThanTheThresholdIsReturnedToTheRelayQueue() {
        UUID eventId = persistEvent();
        claimForPublishing(eventId, Instant.now().minus(Duration.ofMinutes(42)));

        OutboxRecoveryResult result = outboxRecoveryService.recoverStalePublishing();

        assertThat(result.eligible()).isEqualTo(1);
        assertThat(result.requeued()).isEqualTo(1);
        OutboxEvent recovered = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(recovered.getNextAttemptAt()).isNull();
        assertThat(recovered.getPublishedAt()).isNull();
        // The claim was abandoned, not rejected: the retry budget is untouched.
        assertThat(recovered.getAttemptCount()).isZero();
        assertThat(recovered.getRecoveryCycleCount()).isZero();
        assertThat(recovered.getLastFailureCategory())
                .isEqualTo(OutboxRecoveryOrigin.AUTOMATIC_STALE_PUBLISHING.name());
        assertThat(outboxEventRepository.findDueEventIds(OutboxEventStatus.PENDING, Instant.now(), 20))
                .contains(eventId);
    }

    @Test
    void aClaimExactlyAtTheCutoffQualifiesAndOneMillisecondNewerDoesNot() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = now.minus(STALE_AFTER);
        UUID atCutoff = persistEvent();
        UUID justInside = persistEvent();
        claimForPublishing(atCutoff, cutoff);
        claimForPublishing(justInside, cutoff.plusMillis(1));

        OutboxRecoveryResult result = recoveryServiceAt(now).recoverStalePublishing();

        assertThat(result.requeued()).isEqualTo(1);
        assertThat(status(atCutoff)).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(status(justInside)).isEqualTo(OutboxEventStatus.PUBLISHING);

        // The gauge counts on the same side of the same boundary, from the same property.
        OutboxBacklogSnapshot snapshot = outboxEventRepository.loadBacklogSnapshot(cutoff);
        assertThat(snapshot.stuckPublishing()).isZero();
        assertThat(snapshot.publishing()).isEqualTo(1);
    }

    @Test
    void publishedHistoryIsNeverRepublishedByRecovery() {
        UUID publishedId = persistEvent();
        claimForPublishing(publishedId, Instant.now().minus(Duration.ofDays(30)));
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent event = outboxEventRepository.findById(publishedId).orElseThrow();
            event.markPublished(Instant.now().minus(Duration.ofDays(30)));
            outboxEventRepository.save(event);
        });

        OutboxRecoveryResult result = outboxRecoveryService.recoverStalePublishing();

        assertThat(result.eligible()).isZero();
        assertThat(status(publishedId)).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void permanentAndExhaustedFailuresAreNotReanimated() {
        UUID permanent = persistEvent();
        UUID exhausted = persistEvent();
        forceFailedState(permanent, OutboxFailureDisposition.PERMANENT);
        forceFailedState(exhausted, OutboxFailureDisposition.RECOVERY_EXHAUSTED);

        OutboxRecoveryResult result = outboxRecoveryService.recoverStalePublishing();

        assertThat(result.eligible()).isZero();
        assertThat(status(permanent)).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(status(exhausted)).isEqualTo(OutboxEventStatus.FAILED);
    }

    // ------------------------------------------------------------- concurrency

    @Test
    void aSecondRecoveryWorkerCannotRecoverTheSameClaimTwice() {
        UUID eventId = persistEvent();
        Instant claimedAt = Instant.now().minus(Duration.ofMinutes(42));
        claimForPublishing(eventId, claimedAt);
        Instant cutoff = Instant.now().minus(STALE_AFTER);

        // Both workers selected the same id before either updated; the predicate decides.
        int firstWorker = outboxEventRepository.requeueStalePublishing(
                eventId, OutboxEventStatus.PUBLISHING, OutboxEventStatus.PENDING,
                cutoff, OutboxRecoveryOrigin.AUTOMATIC_STALE_PUBLISHING.name(), Instant.now());
        int secondWorker = outboxEventRepository.requeueStalePublishing(
                eventId, OutboxEventStatus.PUBLISHING, OutboxEventStatus.PENDING,
                cutoff, OutboxRecoveryOrigin.AUTOMATIC_STALE_PUBLISHING.name(), Instant.now());

        assertThat(firstWorker).isEqualTo(1);
        assertThat(secondWorker).isZero();
        assertThat(status(eventId)).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxRecoveryService.recoverStalePublishing().eligible()).isZero();
    }

    @Test
    void aRelayThatFinishesFirstKeepsItsResultWhenRecoveryArrivesLate() {
        UUID eventId = persistEvent();
        claimForPublishing(eventId, Instant.now().minus(Duration.ofMinutes(42)));
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
            event.markPublished(Instant.now());
            outboxEventRepository.save(event);
        });

        int recovered = outboxEventRepository.requeueStalePublishing(
                eventId, OutboxEventStatus.PUBLISHING, OutboxEventStatus.PENDING,
                cutoff, OutboxRecoveryOrigin.AUTOMATIC_STALE_PUBLISHING.name(), Instant.now());

        assertThat(recovered).isZero();
        assertThat(status(eventId)).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    // ---------------------------------------------------------------- gauge tie

    @Test
    void theStuckGaugeCountsExactlyWhatRecoveryAcceptsAndClearsOnceItRuns() {
        UUID stale = persistEvent();
        claimForPublishing(stale, Instant.now().minus(Duration.ofMinutes(2)));
        UUID recent = persistEvent();
        claimForPublishing(recent, Instant.now().minusSeconds(5));

        // Two minutes is stuck only under the configured 90s, not under the 5m default.
        assertThat(meterRegistry.get("project3.outbox.stuck").tag("status", "publishing").gauge().value())
                .isEqualTo(1.0);

        OutboxRecoveryResult result = outboxRecoveryService.recoverStalePublishing();

        assertThat(result.requeued()).isEqualTo(1);
        // Read through the gauge's own query rather than the meter, whose one-second snapshot
        // belongs to a scrape window; canonical rows are what the gauge reports.
        OutboxBacklogSnapshot after = outboxEventRepository.loadBacklogSnapshot(
                Instant.now().minus(recoveryProperties.getStalePublishingAge()));
        assertThat(after.stuckPublishing()).isZero();
        assertThat(after.publishing()).isEqualTo(1);
        assertThat(after.pending()).isEqualTo(1);
    }

    // ------------------------------------------------------ end-to-end recovery

    @Test
    void recoveredClaimIsRepublishedByTheNormalRelayUnderTheSameEventId() throws Exception {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        UUID eventId = persistEvent();
        claimForPublishing(eventId, Instant.now().minus(Duration.ofMinutes(42)));

        // The broker accepted the first attempt; the process died before the row could record it.
        OutboxEvent claimed = outboxEventRepository.findById(eventId).orElseThrow();
        outboxMessagePublisher.publish(claimed);

        outboxRecoveryService.recoverStalePublishing();
        outboxRelayService.relay(RelayRequest.scheduledAll(20));

        ArgumentCaptor<String> envelopes = ArgumentCaptor.forClass(String.class);
        verify(kafkaSender, times(2)).send(anyString(), anyString(), envelopes.capture());
        List<String> published = envelopes.getAllValues();

        String beforeCrash = objectMapper.readTree(published.get(0)).path("eventId").asText();
        String afterRecovery = objectMapper.readTree(published.get(1)).path("eventId").asText();
        assertThat(beforeCrash).isEqualTo(eventId.toString());
        assertThat(afterRecovery)
                .as("downstream dedupe keys on the event id, so recovery must not mint a new one")
                .isEqualTo(beforeCrash);
        assertThat(published.get(1)).isEqualTo(published.get(0));

        OutboxEvent settled = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(settled.getPublishedAt()).isNotNull();
    }

    @Test
    void recoveryDrillLeavesEachEventInItsCorrectStateAndOnlyRepublishesTheRecoveredOnes() {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        UUID waiting = persistEvent();
        UUID recentClaim = persistEvent();
        UUID staleClaim = persistEvent();
        claimForPublishing(recentClaim, Instant.now().minusSeconds(5));
        claimForPublishing(staleClaim, Instant.now().minus(Duration.ofMinutes(42)));

        outboxRecoveryService.recoverStalePublishing();

        assertThat(status(waiting)).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(status(recentClaim)).isEqualTo(OutboxEventStatus.PUBLISHING);
        assertThat(status(staleClaim)).isEqualTo(OutboxEventStatus.PENDING);

        outboxRelayService.relay(RelayRequest.scheduledAll(20));

        assertThat(status(waiting)).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(status(staleClaim)).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(status(recentClaim)).isEqualTo(OutboxEventStatus.PUBLISHING);
        verify(kafkaSender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void scanningIsBoundedByBatchSizeAndUsesTheConfiguredCutoff() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OutboxEventStore store = org.mockito.Mockito.mock(OutboxEventStore.class);
        when(store.findStalePublishingIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        new OutboxRecoveryService(store, recoveryProperties, transactionTemplate, Clock.fixed(now, ZoneOffset.UTC))
                .recoverStalePublishing();

        verify(store).findStalePublishingIds(
                OutboxEventStatus.PUBLISHING,
                now.minus(STALE_AFTER),
                recoveryProperties.getBatchSize()
        );
    }

    // ------------------------------------------------------------------ helpers

    private OutboxRecoveryService recoveryServiceAt(Instant now) {
        return new OutboxRecoveryService(
                outboxEventRepository,
                recoveryProperties,
                transactionTemplate,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private UUID persistEvent() {
        return outboxEventRepository.save(new OutboxEvent(
                UUID.randomUUID(),
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "{\"assetId\":\"%s\"}".formatted(UUID.randomUUID())
        )).getId();
    }

    /** The production claim, stamping the moment the relay took the row. */
    private void claimForPublishing(UUID eventId, Instant claimedAt) {
        int claimed = outboxEventRepository.markPublishing(
                eventId,
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PUBLISHING,
                claimedAt
        );
        assertThat(claimed).isEqualTo(1);
    }

    private void forceFailedState(UUID eventId, OutboxFailureDisposition disposition) {
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
            ReflectionTestUtils.setField(event, "status", OutboxEventStatus.FAILED);
            ReflectionTestUtils.setField(event, "failureDisposition", disposition);
            ReflectionTestUtils.setField(event, "updatedAt", Instant.now().minus(Duration.ofDays(1)));
            outboxEventRepository.save(event);
        });
    }

    private OutboxEventStatus status(UUID eventId) {
        return outboxEventRepository.findById(eventId).orElseThrow().getStatus();
    }
}
