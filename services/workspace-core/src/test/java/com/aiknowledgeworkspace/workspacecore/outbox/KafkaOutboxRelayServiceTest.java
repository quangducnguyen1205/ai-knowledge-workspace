package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging.KafkaOutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.application.service.OutboxRelayService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedEventContract;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-kafka-outbox-relay;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.enabled=true",
        "outbox.relay.batch-size=20",
        "outbox.relay.max-attempts=2",
        "outbox.relay.retry-delay=30s",
        "workspace.kafka.enabled=true",
        "workspace.kafka.processing-requested-topic=asset.processing.requested.v1",
        "workspace.kafka.send-timeout=1s"
})
@Transactional
class KafkaOutboxRelayServiceTest {

    @Autowired
    private OutboxEventStore outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxMessagePublisher outboxMessagePublisher;

    @MockBean
    private KafkaOutboxMessagePublisher.KafkaSender kafkaSender;

    @BeforeEach
    void setUp() {
        reset(kafkaSender);
    }

    @Test
    void kafkaPublisherIsActiveOnlyWhenKafkaIsExplicitlyEnabled() {
        assertThat(outboxMessagePublisher).isInstanceOf(KafkaOutboxMessagePublisher.class);
    }

    @Test
    void acknowledgedKafkaSendAllowsRelayToMarkPublished() {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxEvent event = outboxEventRepository.save(newOutboxEvent());

        int processedCount = outboxRelayService.relay(RelayRequest.scheduledAll(20)).processedCount();

        assertThat(processedCount).isEqualTo(1);

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(savedEvent.getPublishedAt()).isNotNull();
        assertThat(savedEvent.getAttemptCount()).isZero();
        assertThat(savedEvent.getLastError()).isNull();
        assertThat(savedEvent.getNextAttemptAt()).isNull();
    }

    @Test
    void failedKafkaSendRecordsRetryMetadata() {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        OutboxEvent event = outboxEventRepository.save(newOutboxEvent());

        int processedCount = outboxRelayService.relay(RelayRequest.scheduledAll(20)).processedCount();

        assertThat(processedCount).isEqualTo(1);

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(1);
        assertThat(savedEvent.getLastError()).isEqualTo("UNKNOWN_PUBLICATION_FAILURE");
        assertThat(savedEvent.getLastFailureCategory()).isEqualTo("UNKNOWN_PUBLICATION_FAILURE");
        assertThat(savedEvent.getFailureDisposition()).isNull();
        assertThat(savedEvent.getPublishedAt()).isNull();
        assertThat(savedEvent.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void secondPublicationFailureTransitionsEventToTerminalFailure() {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("still unavailable")));
        OutboxEvent event = newOutboxEvent();
        recordFailure(event, Instant.now().minusSeconds(5), 5);
        event = outboxEventRepository.save(event);

        int processedCount = outboxRelayService.relay(RelayRequest.scheduledAll(20)).processedCount();

        assertThat(processedCount).isEqualTo(1);
        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(2);
        assertThat(savedEvent.getFailureDisposition()).isEqualTo(OutboxFailureDisposition.UNKNOWN);
        assertThat(savedEvent.getNextAttemptAt()).isNull();
    }

    @Test
    void scheduledRelaySkipsPendingEventBeforeItsRetryDeadline() {
        OutboxEvent event = newOutboxEvent();
        recordFailure(event, Instant.now().plusSeconds(3600), 5);
        event = outboxEventRepository.save(event);

        int processedCount = outboxRelayService.relay(RelayRequest.scheduledAll(20)).processedCount();

        assertThat(processedCount).isZero();
        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void typeScopedRelayHonorsEventTypeAndBatchSize() {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxEvent firstProcessing = outboxEventRepository.save(newOutboxEvent());
        OutboxEvent secondProcessing = outboxEventRepository.save(newOutboxEvent());
        OutboxEvent indexing = outboxEventRepository.save(newIndexingOutboxEvent());

        int processedCount = outboxRelayService.relay(RelayRequest.scheduledForType(
                ProcessingRequestedEventContract.EVENT_TYPE,
                1
        )).processedCount();

        assertThat(processedCount).isEqualTo(1);
        assertThat(outboxEventRepository.findById(firstProcessing.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(secondProcessing.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(indexing.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void oneCandidateFailureDoesNotPreventTheNextCandidateFromPublishing() {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxEvent failingEvent = outboxEventRepository.save(newOutboxEvent());
        OutboxEvent successfulEvent = outboxEventRepository.save(newOutboxEvent());

        int processedCount = outboxRelayService.relay(RelayRequest.scheduledForType(
                ProcessingRequestedEventContract.EVENT_TYPE,
                10
        )).processedCount();

        assertThat(processedCount).isEqualTo(2);
        assertThat(outboxEventRepository.findById(failingEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(successfulEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void explicitRelayRejectsAnEventFromAnotherContract() {
        OutboxEvent indexingEvent = outboxEventRepository.save(newIndexingOutboxEvent());

        assertThatThrownBy(() -> outboxRelayService.relay(RelayRequest.explicit(
                indexingEvent.getId(),
                ProcessingRequestedEventContract.EVENT_TYPE,
                "Manual processing relay only supports processing request events"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only supports processing request events");
    }

    private OutboxEvent newOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                """
                        {
                          "assetId": "%s",
                          "workspaceId": "workspace-1",
                          "storageBucket": "workspace-media",
                          "objectKey": "users/user-1/workspaces/workspace-1/assets/%s/raw/lecture.mp4",
                          "contentType": "video/mp4",
                          "sizeBytes": 12345
                        }
                        """.formatted(assetId, assetId)
        );
    }

    private OutboxEvent newIndexingOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                IndexingRequestedEventContract.EVENT_TYPE,
                IndexingRequestedEventContract.EVENT_VERSION,
                IndexingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\"}".formatted(assetId)
        );
    }

    private void recordFailure(OutboxEvent event, Instant nextAttemptAt, int maxAttempts) {
        event.recordPublishFailure(
                new OutboxFailureClassification(
                        OutboxFailureDisposition.UNKNOWN,
                        "TEST_PUBLICATION_FAILURE"
                ),
                Instant.now(),
                nextAttemptAt,
                maxAttempts,
                Duration.ofSeconds(60),
                3
        );
    }
}
