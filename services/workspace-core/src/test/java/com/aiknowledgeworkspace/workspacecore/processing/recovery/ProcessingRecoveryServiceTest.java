package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.persistence.OutboxEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.result.ConsumedProcessingResultEventRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshotRepository;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingCompatibilityGateway;
import com.aiknowledgeworkspace.workspacecore.processing.infrastructure.persistence.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.workspace.infrastructure.persistence.WorkspaceRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-processing-recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ProcessingRecoveryServiceTest {

    @Autowired
    private ProcessingRecoveryService processingRecoveryService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConsumedProcessingResultEventRepository consumedEventRepository;

    @Autowired
    private AssetTranscriptRowSnapshotRepository transcriptRowSnapshotRepository;

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @MockBean
    private DirectProcessingCompatibilityGateway compatibilityGateway;

    @BeforeEach
    void setUp() {
        consumedEventRepository.deleteAll();
        transcriptRowSnapshotRepository.deleteAll();
        processingJobRepository.deleteAll();
        assetRepository.deleteAll();
        workspaceRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    void requeuesOnlySelectedStalePublishingEventWithoutPublishingIt() {
        OutboxEvent selected = outboxEventRepository.saveAndFlush(newOutboxEvent());
        selected.recordPublishFailure(
                new com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification(
                        com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition.UNKNOWN,
                        "PREVIOUS_FAILURE"
                ),
                Instant.now(),
                Instant.now().minusSeconds(60),
                5,
                Duration.ofSeconds(60),
                3
        );
        selected = outboxEventRepository.saveAndFlush(selected);
        OutboxEvent other = outboxEventRepository.saveAndFlush(newOutboxEvent());

        markPublishing(selected.getId(), Instant.now().minus(Duration.ofMinutes(10)));
        markPublishing(other.getId(), Instant.now().minus(Duration.ofMinutes(10)));

        OutboxDeliveryStatus status = processingRecoveryService.requeueStuckOutboxEventOnce(
                selected.getId(),
                Duration.ofMinutes(5)
        );

        assertThat(status).isEqualTo(OutboxDeliveryStatus.PENDING);
        OutboxEvent savedSelected = outboxEventRepository.findById(selected.getId()).orElseThrow();
        OutboxEvent savedOther = outboxEventRepository.findById(other.getId()).orElseThrow();
        assertThat(savedSelected.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedSelected.getAttemptCount()).isEqualTo(1);
        assertThat(savedSelected.getLastError()).isEqualTo("PREVIOUS_FAILURE");
        assertThat(savedSelected.getNextAttemptAt()).isNull();
        assertThat(savedSelected.getPublishedAt()).isNull();
        assertThat(savedOther.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHING);
    }

    @Test
    void requeueRejectsMissingNonPublishingPublishedOrWrongTypeEvent() {
        UUID missingEventId = UUID.randomUUID();
        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                missingEventId,
                Duration.ZERO
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was not found");

        OutboxEvent pending = outboxEventRepository.saveAndFlush(newOutboxEvent());
        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                pending.getId(),
                Duration.ZERO
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not stuck in PUBLISHING")
                .hasMessageContaining("PENDING");

        OutboxEvent published = newOutboxEvent();
        published.markPublished(Instant.now());
        published = outboxEventRepository.saveAndFlush(published);
        UUID publishedEventId = published.getId();
        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                publishedEventId,
                Duration.ZERO
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already published");

        OutboxEvent wrongType = outboxEventRepository.saveAndFlush(new OutboxEvent(
                "transcript.ready",
                1,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "{}"
        ));
        markPublishing(wrongType.getId(), Instant.now().minus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                wrongType.getId(),
                Duration.ZERO
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only supports asset.processing.requested");
    }

    @Test
    void requeueRejectsPublishingEventThatIsNotOldEnough() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());
        markPublishing(event.getId(), Instant.now());

        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                event.getId(),
                Duration.ofMinutes(5)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not old enough");

        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHING);
    }

    @Test
    void requeueRejectsNegativeMinimumAgeAndLeavesEventPublishing() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());
        markPublishing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                event.getId(),
                Duration.ofSeconds(-1)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum-publishing-age")
                .hasMessageContaining("must not be negative");

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHING);
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    @Test
    void requeueRejectsNullMinimumAgeAndLeavesEventPublishing() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());
        markPublishing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> processingRecoveryService.requeueStuckOutboxEventOnce(
                event.getId(),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum-publishing-age")
                .hasMessageContaining("is required");

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHING);
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    @Test
    void requeueAllowsZeroMinimumAgeForExplicitLocalSmoke() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());
        markPublishing(event.getId(), Instant.now());

        OutboxDeliveryStatus status = processingRecoveryService.requeueStuckOutboxEventOnce(
                event.getId(),
                Duration.ZERO
        );

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(status).isEqualTo(OutboxDeliveryStatus.PENDING);
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    private OutboxEvent newOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\"}".formatted(assetId)
        );
    }

    private void markPublishing(UUID eventId, Instant updatedAt) {
        jdbcTemplate.update(
                "update outbox_events set status = 'PUBLISHING', next_attempt_at = null, updated_at = ? where id = ?",
                Timestamp.from(updatedAt),
                eventId
        );
    }
}
