package com.aiknowledgeworkspace.workspacecore;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging.KafkaOutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.outbox.application.service.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.processing.api.TranscriptArtifactGateway;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.service.ProcessingResultApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchTranscriptAdapter;
import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.result.AssetIndexingHandleResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.AssetIndexingApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traceability drill: starting from one assetId (or the request event id), the Spring logs
 * alone must reveal every asynchronous transition of a processing request — created,
 * published to Kafka, result received, result applied, indexing requested, indexing
 * executed — using only identifiers the lifecycle already carries.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-lifecycle-trace;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
        "workspace.kafka.send-timeout=1s",
        "workspace.search.indexing.auto-request-enabled=true"
})
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ProcessingLifecycleTraceabilityTest {

    @Autowired
    private ProcessingRequestUseCase processingRequestUseCase;

    @Autowired
    private ProcessingJobStore processingJobRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private ProcessingResultApplicationService processingResultEventHandler;

    @Autowired
    private AssetIndexingApplicationService assetIndexingEventHandler;

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @MockBean
    private KafkaOutboxMessagePublisher.KafkaSender kafkaSender;

    @MockBean
    private TranscriptArtifactGateway transcriptArtifactGateway;

    @MockBean
    private ElasticsearchTranscriptAdapter elasticsearchTranscriptAdapter;

    @Test
    void oneAssetIdRevealsEveryLifecycleTransitionInTheLogs(CapturedOutput output) {
        when(kafkaSender.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
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

        processingRequestUseCase.createKafkaJobAndRequest(new ProcessingRequestCommand(
                assetId,
                workspace.getId(),
                "user-1",
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "lecture.mp4",
                "video/mp4",
                123L
        ));
        UUID requestEventId = processingJobRepository.findByAssetId(assetId)
                .orElseThrow().getProcessingRequestEventId();

        outboxRelayService.relay(RelayRequest.scheduledAll(20));

        UUID resultEventId = UUID.randomUUID();
        when(transcriptArtifactGateway.loadRows(requestEventId)).thenReturn(List.of(
                new ProcessingTranscriptRow("row-1", "video-1", 0, 0L, 1250L,
                        "Private transcript sentence one", "2026-06-21T00:00:00Z"),
                new ProcessingTranscriptRow("row-2", "video-1", 1, 1250L, 2500L,
                        "Private transcript sentence two", "2026-06-21T00:00:00Z")
        ));
        processingResultEventHandler.handle("""
                {
                  "eventId": "%s",
                  "eventType": "transcript.ready",
                  "eventVersion": 1,
                  "aggregateType": "ASSET",
                  "aggregateId": "%s",
                  "eventKey": "%s",
                  "causationEventId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "processingRequestId": "%s"
                  }
                }
                """.formatted(resultEventId, assetId, assetId, requestEventId, Instant.now(), requestEventId));

        AssetSearchIndexJob indexingJob = searchIndexJobRepository
                .findByAssetAndStatuses(assetId, List.of(AssetSearchIndexJobStatus.PENDING))
                .get(0);
        UUID indexingEventId = indexingJob.getRequestOutboxEventId();

        ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
        outboxRelayService.relay(RelayRequest.scheduledAll(20));
        org.mockito.Mockito.verify(kafkaSender, org.mockito.Mockito.times(2))
                .send(anyString(), anyString(), envelopeCaptor.capture());
        String indexingEnvelope = envelopeCaptor.getAllValues().stream()
                .filter(envelope -> envelope.contains(IndexingRequestedEventContract.EVENT_TYPE))
                .findFirst()
                .orElseThrow();

        AssetIndexingHandleResult indexingResult = assetIndexingEventHandler.handle(indexingEnvelope);

        assertThat(indexingResult.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(assetRepository.findById(assetId).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.SEARCHABLE);
        assertThat(processingJobRepository.findByAssetId(assetId).orElseThrow().getProcessingJobStatus())
                .isEqualTo(ProcessingJobStatus.SUCCEEDED);

        assertThat(output.getAll())
                .contains("Processing request created assetId=" + assetId)
                .contains("Outbox event published eventId=" + requestEventId
                        + " eventType=" + ProcessingRequestedEventContract.EVENT_TYPE
                        + " aggregateId=" + assetId)
                .contains("Processing result received resultEventId=" + resultEventId
                        + " resultType=transcript.ready assetId=" + assetId
                        + " requestEventId=" + requestEventId)
                .contains("Processing result applied resultEventId=" + resultEventId)
                .contains("Indexing requested assetId=" + assetId
                        + " indexingJobId=" + indexingJob.getId()
                        + " requestEventId=" + indexingEventId)
                .contains("Outbox event published eventId=" + indexingEventId
                        + " eventType=" + IndexingRequestedEventContract.EVENT_TYPE
                        + " aggregateId=" + assetId)
                .contains("Indexing started assetId=" + assetId + " indexingJobId=" + indexingJob.getId())
                .contains("Indexing completed assetId=" + assetId
                        + " indexingJobId=" + indexingJob.getId()
                        + " indexedRowCount=2")
                .doesNotContain("Private transcript sentence");
    }
}
