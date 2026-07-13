package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRecoveryResult;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventContract;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-outbox-recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.recovery.enabled=true",
        "outbox.recovery.interval=30s",
        "outbox.recovery.cooldown=60s",
        "outbox.recovery.batch-size=50",
        "outbox.recovery.max-cycles=3"
})
class OutboxRecoveryServiceTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void eligibilityIncludesOnlyCooledDownTransientFailedRows() {
        Instant now = Instant.now();
        OutboxEvent eligible = saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.TRANSIENT,
                now.minusSeconds(1),
                0
        );
        saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.TRANSIENT,
                now.plusSeconds(60),
                0
        );
        saveWithRecoveryState(
                newIndexingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.PERMANENT,
                null,
                0
        );
        saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.UNKNOWN,
                null,
                0
        );
        saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.RECOVERY_EXHAUSTED,
                null,
                3
        );
        saveWithRecoveryState(newProcessingEvent(), OutboxEventStatus.PENDING, null, null, 0);
        saveWithRecoveryState(newProcessingEvent(), OutboxEventStatus.PUBLISHING, null, null, 0);
        saveWithRecoveryState(newProcessingEvent(), OutboxEventStatus.PUBLISHED, null, null, 0);

        List<UUID> ids = repository.findEligibleRecoveryIds(
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.TRANSIENT,
                now,
                3,
                PageRequest.of(0, 50)
        );

        assertThat(ids).containsExactly(eligible.getId());
    }

    @Test
    void atomicallyRequeuesProcessingAndIndexingRowsWithoutChangingEventIdentity() {
        OutboxEvent processing = saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.TRANSIENT,
                Instant.now().minusSeconds(1),
                1
        );
        OutboxEvent indexing = saveWithRecoveryState(
                newIndexingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.TRANSIENT,
                Instant.now().minusSeconds(1),
                0
        );
        String processingPayload = processing.getPayload();
        String processingKey = processing.getEventKey();

        OutboxRecoveryResult first = recoveryService.reconcileEligibleFailures();
        OutboxRecoveryResult second = recoveryService.reconcileEligibleFailures();

        assertThat(first.eligible()).isEqualTo(2);
        assertThat(first.requeued()).isEqualTo(2);
        assertThat(second.requeued()).isZero();

        OutboxEvent savedProcessing = repository.findById(processing.getId()).orElseThrow();
        OutboxEvent savedIndexing = repository.findById(indexing.getId()).orElseThrow();
        assertThat(savedProcessing.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedProcessing.getAttemptCount()).isZero();
        assertThat(savedProcessing.getRecoveryCycleCount()).isEqualTo(2);
        assertThat(savedProcessing.getId()).isEqualTo(processing.getId());
        assertThat(savedProcessing.getEventKey()).isEqualTo(processingKey);
        assertThat(savedProcessing.getPayload()).isEqualTo(processingPayload);
        assertThat(savedIndexing.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedIndexing.getRecoveryCycleCount()).isEqualTo(1);
    }

    @Test
    void finalTransientFailureAfterMaximumCyclesBecomesRecoveryExhausted() {
        OutboxEvent event = newProcessingEvent();
        ReflectionTestUtils.setField(event, "recoveryCycleCount", 3);
        Instant failedAt = Instant.now();

        event.recordPublishFailure(
                new OutboxFailureClassification(
                        OutboxFailureDisposition.TRANSIENT,
                        "KAFKA_RETRYABLE_FAILURE"
                ),
                failedAt,
                failedAt.plusSeconds(30),
                1,
                Duration.ofSeconds(60),
                3
        );

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getFailureDisposition()).isEqualTo(OutboxFailureDisposition.RECOVERY_EXHAUSTED);
        assertThat(event.getNextRecoveryAt()).isNull();
        assertThat(event.getRecoveryExhaustedAt()).isEqualTo(failedAt);
    }

    @Test
    void unknownHistoricalFailureAndPublishingClaimRemainOutsideAutomaticRecovery() {
        saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.UNKNOWN,
                null,
                0
        );
        OutboxEvent publishing = saveWithRecoveryState(
                newProcessingEvent(),
                OutboxEventStatus.PUBLISHING,
                OutboxFailureDisposition.TRANSIENT,
                Instant.now().minusSeconds(60),
                0
        );

        OutboxRecoveryResult result = recoveryService.reconcileEligibleFailures();

        assertThat(result.requeued()).isZero();
        assertThat(repository.findById(publishing.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHING);
    }

    private OutboxEvent saveWithRecoveryState(
            OutboxEvent event,
            OutboxEventStatus status,
            OutboxFailureDisposition disposition,
            Instant nextRecoveryAt,
            int recoveryCycleCount
    ) {
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "failureDisposition", disposition);
        ReflectionTestUtils.setField(event, "nextRecoveryAt", nextRecoveryAt);
        ReflectionTestUtils.setField(event, "recoveryCycleCount", recoveryCycleCount);
        ReflectionTestUtils.setField(event, "attemptCount", status == OutboxEventStatus.FAILED ? 5 : 0);
        ReflectionTestUtils.setField(event, "lastFailureCategory", "TEST_CATEGORY");
        return repository.saveAndFlush(event);
    }

    private OutboxEvent newProcessingEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                ProcessingRequestedEventContract.EVENT_TYPE,
                1,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\"}".formatted(assetId)
        );
    }

    private OutboxEvent newIndexingEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                IndexingRequestedEventContract.EVENT_TYPE,
                1,
                IndexingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\",\"indexingJobId\":\"%s\",\"snapshotFingerprint\":\"abc\"}"
                        .formatted(assetId, UUID.randomUUID())
        );
    }
}
