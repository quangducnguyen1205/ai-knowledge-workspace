package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        processingJobRepository.deleteAll();
        assetRepository.deleteAll();
        workspaceRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    void persistsPendingOutboxEventWithFlywayManagedSchemaThroughRelayStateMigration() {
        UUID assetId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
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
        assertThat(savedEvent.getFailureDisposition()).isNull();
        assertThat(savedEvent.getRecoveryCycleCount()).isZero();
        assertThat(savedEvent.getNextRecoveryAt()).isNull();
        assertThat(savedEvent.getLastFailureCategory()).isNull();
        assertThat(savedEvent.getRecoveryExhaustedAt()).isNull();

        List<OutboxEvent> aggregateEvents = outboxEventRepository.findByAggregateTypeAndAggregateId(
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                assetId
        );
        assertThat(aggregateEvents).hasSize(1);
        assertThat(aggregateEvents.get(0).getEventType()).isEqualTo(ProcessingRequestedEventContract.EVENT_TYPE);
    }

    @Test
    void preservesPreassignedOutboxEventIdForProcessingJobCorrelationAndKafkaEnvelope() throws Exception {
        UUID preassignedEventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Workspace workspace = workspaceRepository.save(new Workspace(
                UUID.randomUUID(),
                "Algorithms",
                "user-1",
                false
        ));
        assetRepository.save(new Asset(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.PROCESSING,
                workspace,
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        ));

        OutboxEvent event = new OutboxEvent(
                preassignedEventId,
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                """
                        {
                          "assetId": "%s",
                          "storageBucket": "workspace-media",
                          "objectKey": "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4",
                          "contentType": "video/mp4",
                          "sizeBytes": 123
                        }
                        """.formatted(assetId, workspace.getId(), assetId)
        );

        outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        OutboxEvent reloadedEvent = outboxEventRepository.findById(preassignedEventId).orElseThrow();
        assertThat(reloadedEvent.getId()).isEqualTo(preassignedEventId);

        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                null,
                null,
                ProcessingJobStatus.PENDING,
                "kafka_request_pending"
        );
        processingJob.setProcessingRequestEventId(preassignedEventId);
        ProcessingJob savedProcessingJob = processingJobRepository.saveAndFlush(processingJob);
        UUID processingJobId = savedProcessingJob.getId();
        entityManager.clear();

        assertThat(processingJobRepository.findByAssetIdAndProcessingRequestEventId(assetId, preassignedEventId))
                .map(ProcessingJob::getId)
                .contains(processingJobId);
        ProcessingJob reloadedProcessingJob = processingJobRepository.findById(processingJobId).orElseThrow();
        assertThat(reloadedProcessingJob.getFastapiTaskId()).isNull();
        assertThat(reloadedProcessingJob.getFastapiVideoId()).isNull();

        KafkaOutboxMessagePublisher publisher = new KafkaOutboxMessagePublisher(
                kafkaProperties(),
                objectMapper,
                (topic, key, value) -> CompletableFuture.completedFuture(null)
        );
        String envelope = publisher.buildEnvelope(reloadedEvent);
        JsonNode envelopeJson = objectMapper.readTree(envelope);

        assertThat(envelopeJson.path("eventId").asText()).isEqualTo(preassignedEventId.toString());
    }

    private WorkspaceKafkaProperties kafkaProperties() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();
        properties.setProcessingRequestedTopic("asset.processing.requested.v1");
        return properties;
    }
}
