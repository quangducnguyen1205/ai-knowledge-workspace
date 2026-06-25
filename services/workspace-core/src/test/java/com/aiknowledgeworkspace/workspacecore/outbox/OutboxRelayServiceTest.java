package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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
    void requestRelayPublishesOnlyDueProcessingRequestEventsAndHonorsBatchSize() {
        OutboxEvent firstRequestEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        OutboxEvent secondRequestEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        OutboxEvent thirdRequestEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        OutboxEvent indexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        OutboxEvent futureRequestEvent = newOutboxEvent();
        futureRequestEvent.recordPublishFailure("not yet", Instant.now().plusSeconds(3600), 5);
        futureRequestEvent = outboxEventRepository.saveAndFlush(futureRequestEvent);

        int processedCount = outboxRelayService.relayDueProcessingRequestEvents(2);

        assertThat(processedCount).isEqualTo(2);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds())
                .containsExactly(firstRequestEvent.getId(), secondRequestEvent.getId());
        assertThat(outboxEventRepository.findById(firstRequestEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(secondRequestEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(thirdRequestEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(indexingEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(futureRequestEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void requestRelayFailureForOneCandidateDoesNotPreventLaterCandidate() {
        OutboxEvent failingRequestEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        OutboxEvent successfulRequestEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        fakeOutboxMessagePublisher.failEventWith(failingRequestEvent.getId(), "broker unavailable");

        int processedCount = outboxRelayService.relayDueProcessingRequestEvents(10);

        assertThat(processedCount).isEqualTo(2);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds())
                .containsExactly(successfulRequestEvent.getId());

        OutboxEvent savedFailingEvent = outboxEventRepository.findById(failingRequestEvent.getId()).orElseThrow();
        assertThat(savedFailingEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedFailingEvent.getAttemptCount()).isEqualTo(1);
        assertThat(savedFailingEvent.getLastError()).isEqualTo("broker unavailable");
        assertThat(savedFailingEvent.getNextAttemptAt()).isAfter(Instant.now());

        OutboxEvent savedSuccessfulEvent = outboxEventRepository.findById(successfulRequestEvent.getId()).orElseThrow();
        assertThat(savedSuccessfulEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void indexingRelayPublishesOnlyDueIndexingEventsAndHonorsBatchSize() {
        OutboxEvent firstIndexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        OutboxEvent secondIndexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        OutboxEvent thirdIndexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        OutboxEvent processingRequestEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        OutboxEvent resultEvent = outboxEventRepository.saveAndFlush(newResultOutboxEvent());
        OutboxEvent futureIndexingEvent = newIndexingOutboxEvent();
        futureIndexingEvent.recordPublishFailure("not yet", Instant.now().plusSeconds(3600), 5);
        futureIndexingEvent = outboxEventRepository.saveAndFlush(futureIndexingEvent);

        int processedCount = outboxRelayService.relayDueIndexingRequestEvents(2);

        assertThat(processedCount).isEqualTo(2);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds())
                .containsExactly(firstIndexingEvent.getId(), secondIndexingEvent.getId());
        assertThat(outboxEventRepository.findById(firstIndexingEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(secondIndexingEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(thirdIndexingEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(processingRequestEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(resultEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventRepository.findById(futureIndexingEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void indexingRelayFailureForOneCandidateDoesNotPreventLaterCandidate() {
        OutboxEvent failingIndexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        OutboxEvent successfulIndexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        fakeOutboxMessagePublisher.failEventWith(failingIndexingEvent.getId(), "broker unavailable");

        int processedCount = outboxRelayService.relayDueIndexingRequestEvents(10);

        assertThat(processedCount).isEqualTo(2);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds())
                .containsExactly(successfulIndexingEvent.getId());

        OutboxEvent savedFailingEvent = outboxEventRepository.findById(failingIndexingEvent.getId()).orElseThrow();
        assertThat(savedFailingEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedFailingEvent.getAttemptCount()).isEqualTo(1);
        assertThat(savedFailingEvent.getLastError()).isEqualTo("broker unavailable");
        assertThat(savedFailingEvent.getNextAttemptAt()).isAfter(Instant.now());

        OutboxEvent savedSuccessfulEvent = outboxEventRepository.findById(successfulIndexingEvent.getId()).orElseThrow();
        assertThat(savedSuccessfulEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void indexingRelayDoesNotRequireSensitivePayloadData() {
        OutboxEvent indexingEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());

        int processedCount = outboxRelayService.relayDueIndexingRequestEvents(1);

        assertThat(processedCount).isEqualTo(1);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).containsExactly(indexingEvent.getId());
        assertThat(indexingEvent.getPayload())
                .contains("assetId", "indexingJobId", "snapshotFingerprint")
                .doesNotContain("transcript text", "objectKey", "storageBucket", "credential", "token", "password");
    }

    @Test
    void scopedRelayPublishesOnlySelectedDueRequestEvent() {
        OutboxEvent selectedEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());
        OutboxEvent unrelatedDueEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());

        OutboxEventStatus status = outboxRelayService.relayEventByIdOnce(selectedEvent.getId());

        assertThat(status).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).containsExactly(selectedEvent.getId());

        OutboxEvent savedSelectedEvent = outboxEventRepository.findById(selectedEvent.getId()).orElseThrow();
        OutboxEvent savedUnrelatedEvent = outboxEventRepository.findById(unrelatedDueEvent.getId()).orElseThrow();
        assertThat(savedSelectedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(savedUnrelatedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedUnrelatedEvent.getPublishedAt()).isNull();
    }

    @Test
    void scopedRelayRejectsMissingEvent() {
        UUID missingEventId = UUID.randomUUID();

        assertThatThrownBy(() -> outboxRelayService.relayEventByIdOnce(missingEventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox event was not found")
                .hasMessageContaining(missingEventId.toString());

        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();
    }

    @Test
    void scopedRelayRejectsNonRequestEventType() {
        UUID assetId = UUID.randomUUID();
        OutboxEvent event = outboxEventRepository.saveAndFlush(new OutboxEvent(
                "transcript.ready",
                1,
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\"}".formatted(assetId)
        ));

        assertThatThrownBy(() -> outboxRelayService.relayEventByIdOnce(event.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only supports asset.processing.requested");

        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();
    }

    @Test
    void scopedRelayRejectsAlreadyPublishedEvent() {
        OutboxEvent event = newOutboxEvent();
        event.markPublished(Instant.now());
        event = outboxEventRepository.saveAndFlush(event);
        UUID eventId = event.getId();

        assertThatThrownBy(() -> outboxRelayService.relayEventByIdOnce(eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already published");

        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();
    }

    @Test
    void scopedRelayRejectsFailedEvent() {
        OutboxEvent event = newOutboxEvent();
        event.recordPublishFailure("terminal failure", Instant.now().minusSeconds(5), 1);
        event = outboxEventRepository.saveAndFlush(event);
        UUID eventId = event.getId();

        assertThatThrownBy(() -> outboxRelayService.relayEventByIdOnce(eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not eligible")
                .hasMessageContaining("FAILED");

        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();
    }

    @Test
    void scopedRelayRejectsPendingEventScheduledForFutureRetry() {
        OutboxEvent event = newOutboxEvent();
        event.recordPublishFailure("wait before retry", Instant.now().plusSeconds(3600), 5);
        event = outboxEventRepository.saveAndFlush(event);
        UUID eventId = event.getId();

        assertThatThrownBy(() -> outboxRelayService.relayEventByIdOnce(eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not due for relay");

        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();
    }

    @Test
    void scopedRelayPreservesRetryBehaviorWhenPublisherFails() {
        fakeOutboxMessagePublisher.failWith("publisher unavailable");
        OutboxEvent event = outboxEventRepository.saveAndFlush(newOutboxEvent());

        OutboxEventStatus status = outboxRelayService.relayEventByIdOnce(event.getId());

        assertThat(status).isEqualTo(OutboxEventStatus.PENDING);

        OutboxEvent savedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getAttemptCount()).isEqualTo(1);
        assertThat(savedEvent.getLastError()).isEqualTo("publisher unavailable");
        assertThat(savedEvent.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(savedEvent.getPublishedAt()).isNull();
    }

    @Test
    void scopedIndexingRelayPublishesOnlySelectedDueIndexingEvent() {
        OutboxEvent selectedEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());
        OutboxEvent unrelatedDueEvent = outboxEventRepository.saveAndFlush(newIndexingOutboxEvent());

        OutboxEventStatus status = outboxRelayService.relayIndexingEventByIdOnce(selectedEvent.getId());

        assertThat(status).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).containsExactly(selectedEvent.getId());
        assertThat(outboxEventRepository.findById(selectedEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(unrelatedDueEvent.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void scopedIndexingRelayRejectsProcessingRequestEvent() {
        OutboxEvent processingEvent = outboxEventRepository.saveAndFlush(newOutboxEvent());

        assertThatThrownBy(() -> outboxRelayService.relayIndexingEventByIdOnce(processingEvent.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asset.indexing.requested");

        assertThat(fakeOutboxMessagePublisher.publishedEventIds()).isEmpty();
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

    private OutboxEvent newIndexingOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        UUID indexingJobId = UUID.randomUUID();
        return new OutboxEvent(
                OutboxEventFactory.ASSET_INDEXING_REQUESTED,
                OutboxEventFactory.ASSET_INDEXING_REQUESTED_VERSION,
                OutboxEventFactory.ASSET_INDEXING_AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\",\"indexingJobId\":\"%s\",\"snapshotFingerprint\":\"abc123\"}"
                        .formatted(assetId, indexingJobId)
        );
    }

    private OutboxEvent newResultOutboxEvent() {
        UUID assetId = UUID.randomUUID();
        return new OutboxEvent(
                "transcript.ready",
                1,
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\",\"status\":\"ready\"}".formatted(assetId)
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
        private final Map<UUID, String> eventFailures = new HashMap<>();
        private String failureMessage;

        @Override
        public void publish(OutboxEvent event) {
            publishedStatuses.add(event.getStatus());
            if (eventFailures.containsKey(event.getId())) {
                throw new IllegalStateException(eventFailures.get(event.getId()));
            }
            if (failureMessage != null) {
                throw new IllegalStateException(failureMessage);
            }
            publishedEventIds.add(event.getId());
        }

        void failWith(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        void failEventWith(UUID eventId, String failureMessage) {
            eventFailures.put(eventId, failureMessage);
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
            eventFailures.clear();
            failureMessage = null;
        }
    }
}
