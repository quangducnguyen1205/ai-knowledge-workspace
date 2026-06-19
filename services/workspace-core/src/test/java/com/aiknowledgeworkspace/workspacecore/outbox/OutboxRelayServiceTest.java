package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.ClassUtils;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-outbox-relay;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.enabled=true",
        "outbox.relay.batch-size=20",
        "outbox.relay.max-attempts=2",
        "outbox.relay.retry-delay=30s"
})
class OutboxRelayServiceTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private FakeOutboxMessagePublisher fakeOutboxMessagePublisher;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        fakeOutboxMessagePublisher.reset();
    }

    @Test
    void relayPublishesDuePendingEventAndMarksPublished() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());

        int processedCount = outboxRelayService.relayDueEvents();

        assertThat(processedCount).isEqualTo(1);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).containsExactly(event.getId());
        assertThat(fakeOutboxMessagePublisher.publishedStatuses()).containsExactly(OutboxEventStatus.PUBLISHING);

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(savedEvent.getAttemptCount()).isZero();
        assertThat(savedEvent.getPublishedAt()).isNotNull();
        assertThat(savedEvent.getNextAttemptAt()).isNull();
        assertThat(savedEvent.getLastError()).isNull();
    }

    @Test
    void relayIncrementsAttemptsAndSchedulesRetryWhenPublisherFails() {
        fakeOutboxMessagePublisher.failWith("publisher unavailable");
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());

        int processedCount = outboxRelayService.relayDueEvents();

        assertThat(processedCount).isEqualTo(1);

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(1);
        assertThat(savedEvent.getLastError()).isEqualTo("publisher unavailable");
        assertThat(savedEvent.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    @Test
    void relayMarksEventFailedAfterMaxAttempts() {
        fakeOutboxMessagePublisher.failWith("still down");
        OutboxEvent event = newOutboxEvent();
        event.recordPublishFailure("previous failure", Instant.now().minusSeconds(5), 5);
        event = outboxEventRepository.saveAndFlush(event);

        int processedCount = outboxRelayService.relayDueEvents();

        assertThat(processedCount).isEqualTo(1);

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(2);
        assertThat(savedEvent.getLastError()).isEqualTo("still down");
        assertThat(savedEvent.getNextAttemptAt()).isNull();
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    @Test
    void relaySkipsPendingEventScheduledForFutureRetry() {
        OutboxEvent event = newOutboxEvent();
        event.recordPublishFailure("wait before retry", Instant.now().plusSeconds(3600), 5);
        event = outboxEventRepository.saveAndFlush(event);

        int processedCount = outboxRelayService.relayDueEvents();

        assertThat(processedCount).isZero();
        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(1);
        assertThat(savedEvent.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void relayFoundationDoesNotRequireKafkaOnClasspath() {
        assertThat(ClassUtils.isPresent("org.springframework.kafka.core.KafkaTemplate", getClass().getClassLoader()))
                .isFalse();
    }

    @Test
    void relayPropertiesAreDisabledByDefault() {
        assertThat(new OutboxRelayProperties().isEnabled()).isFalse();
    }

    private OutboxEvent newOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED_VERSION,
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\"}".formatted(assetId)
        );
    }

    @TestConfiguration
    static class OutboxRelayServiceTestConfiguration {

        @Bean
        @Primary
        FakeOutboxMessagePublisher fakeOutboxMessagePublisher() {
            return new FakeOutboxMessagePublisher();
        }
    }

    static class FakeOutboxMessagePublisher implements OutboxMessagePublisher {

        private final List<UUID> publishedEventIds = new ArrayList<>();
        private final List<OutboxEventStatus> publishedStatuses = new ArrayList<>();
        private String failureMessage;

        @Override
        public void publish(OutboxEvent event) {
            publishedStatuses.add(event.getStatus());
            if (failureMessage != null) {
                throw new IllegalStateException(failureMessage);
            }
            publishedEventIds.add(event.getId());
        }

        void failWith(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        List<UUID> publishedEventIds() {
            return publishedEventIds;
        }

        List<OutboxEventStatus> publishedStatuses() {
            return publishedStatuses;
        }

        void reset() {
            publishedEventIds.clear();
            publishedStatuses.clear();
            failureMessage = null;
        }
    }
}
