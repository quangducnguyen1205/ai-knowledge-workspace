package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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
class KafkaOutboxRelayServiceTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxMessagePublisher outboxMessagePublisher;

    @MockBean
    private KafkaOutboxMessagePublisher.KafkaSender kafkaSender;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
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
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());

        int processedCount = outboxRelayService.relayDueEvents();

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
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());

        int processedCount = outboxRelayService.relayDueEvents();

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

    private OutboxEvent newOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED_VERSION,
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
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
}
