package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-outbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void persistsPendingOutboxEventWithFlywayManagedSchemaThroughRelayStateMigration() {
        UUID assetId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED_VERSION,
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                "{\"assetId\":\"%s\"}".formatted(assetId)
        );

        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(event);

        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getEventVersion()).isEqualTo(1);
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getAttemptCount()).isZero();
        assertThat(savedEvent.getCreatedAt()).isNotNull();
        assertThat(savedEvent.getUpdatedAt()).isNotNull();
        assertThat(savedEvent.getPublishedAt()).isNull();

        List<OutboxEvent> aggregateEvents = outboxEventRepository.findByAggregateTypeAndAggregateId(
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
                assetId
        );
        assertThat(aggregateEvents).hasSize(1);
        assertThat(aggregateEvents.get(0).getEventType()).isEqualTo(OutboxEventFactory.ASSET_PROCESSING_REQUESTED);
    }
}
