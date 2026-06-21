package com.aiknowledgeworkspace.workspacecore.processing.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshotRepository;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-processing-result;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ProcessingResultEventHandlerTest {

    private static final String FASTAPI_DIRECT_UPLOAD_TASK_ID = "task-1";

    @Autowired
    private ProcessingResultEventHandler processingResultEventHandler;

    @Autowired
    private ConsumedProcessingResultEventRepository consumedEventRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private AssetTranscriptRowSnapshotRepository transcriptRowSnapshotRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @MockBean
    private FastApiProcessingClient fastApiProcessingClient;

    @BeforeEach
    void setUp() {
        consumedEventRepository.deleteAll();
        transcriptRowSnapshotRepository.deleteAll();
        processingJobRepository.deleteAll();
        assetRepository.deleteAll();
        workspaceRepository.deleteAll();
        reset(fastApiProcessingClient);
    }

    @Test
    void validTranscriptReadyRetrievesArtifactsAndMarksAssetReady() {
        UUID processingRequestEventId = UUID.randomUUID();
        Asset asset = persistedAsset(AssetStatus.PROCESSING, ProcessingJobStatus.RUNNING, processingRequestEventId);
        UUID eventId = UUID.randomUUID();
        when(fastApiProcessingClient.getTranscriptArtifactRows(processingRequestEventId.toString())).thenReturn(List.of(
                transcriptRow("row-1", 0, "Welcome to graph search"),
                transcriptRow("row-2", 1, "Then we compare breadth first search")
        ));

        ProcessingResultHandleResult result = processingResultEventHandler.handle(
                transcriptReadyEvent(eventId, asset.getId(), processingRequestEventId, processingRequestEventId)
        );

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.status()).isEqualTo(ConsumedProcessingResultEventStatus.APPLIED);
        assertThat(result.applied()).isTrue();

        ConsumedProcessingResultEvent consumedEvent = consumedEventRepository.findById(eventId).orElseThrow();
        assertThat(consumedEvent.getStatus()).isEqualTo(ConsumedProcessingResultEventStatus.APPLIED);
        assertThat(consumedEvent.getProcessedAt()).isNotNull();
        assertThat(consumedEvent.getErrorDetail()).isNull();

        Asset savedAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(savedAsset.getStatus()).isEqualTo(AssetStatus.TRANSCRIPT_READY);

        ProcessingJob processingJob = processingJobRepository.findByAssetId(asset.getId()).orElseThrow();
        assertThat(processingJob.getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.SUCCEEDED);
        assertThat(processingJob.getProcessingRequestEventId()).isEqualTo(processingRequestEventId);
        assertThat(processingJob.getRawUpstreamTaskState()).isEqualTo("transcript.ready");

        List<AssetTranscriptRowSnapshot> transcriptRows = transcriptRowSnapshotRepository.findByAssetId(asset.getId());
        assertThat(transcriptRows).hasSize(2);
        assertThat(transcriptRows).extracting(AssetTranscriptRowSnapshot::getSegmentIndex).containsExactly(0, 1);
        assertThat(transcriptRows).extracting(AssetTranscriptRowSnapshot::getText)
                .containsExactly("Welcome to graph search", "Then we compare breadth first search");
    }

    @Test
    void duplicateEventIdDoesNotApplyTranscriptReadyTwice() {
        UUID processingRequestEventId = UUID.randomUUID();
        Asset asset = persistedAsset(AssetStatus.PROCESSING, ProcessingJobStatus.RUNNING, processingRequestEventId);
        UUID eventId = UUID.randomUUID();
        String eventJson = transcriptReadyEvent(eventId, asset.getId(), processingRequestEventId, processingRequestEventId);
        when(fastApiProcessingClient.getTranscriptArtifactRows(processingRequestEventId.toString())).thenReturn(List.of(
                transcriptRow("row-1", 0, "One durable transcript row")
        ));

        ProcessingResultHandleResult firstResult = processingResultEventHandler.handle(eventJson);
        ProcessingResultHandleResult duplicateResult = processingResultEventHandler.handle(eventJson);

        assertThat(firstResult.applied()).isTrue();
        assertThat(duplicateResult.applied()).isFalse();
        assertThat(duplicateResult.status()).isEqualTo(ConsumedProcessingResultEventStatus.APPLIED);
        verify(fastApiProcessingClient, times(1)).getTranscriptArtifactRows(processingRequestEventId.toString());
        assertThat(transcriptRowSnapshotRepository.findByAssetId(asset.getId())).hasSize(1);
    }

    @Test
    void invalidTranscriptArtifactsDoNotMarkAssetReady() {
        UUID processingRequestEventId = UUID.randomUUID();
        Asset asset = persistedAsset(AssetStatus.PROCESSING, ProcessingJobStatus.RUNNING, processingRequestEventId);
        UUID eventId = UUID.randomUUID();
        when(fastApiProcessingClient.getTranscriptArtifactRows(processingRequestEventId.toString())).thenReturn(List.of(
                transcriptRow("row-1", 0, "Useful row"),
                transcriptRow("row-2", 1, " ")
        ));

        ProcessingResultHandleResult result = processingResultEventHandler.handle(
                transcriptReadyEvent(eventId, asset.getId(), processingRequestEventId, processingRequestEventId)
        );

        assertThat(result.status()).isEqualTo(ConsumedProcessingResultEventStatus.FAILED);
        assertThat(result.applied()).isFalse();
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.PROCESSING);
        assertThat(processingJobRepository.findByAssetId(asset.getId()).orElseThrow().getProcessingJobStatus())
                .isEqualTo(ProcessingJobStatus.RUNNING);
        assertThat(transcriptRowSnapshotRepository.findByAssetId(asset.getId())).isEmpty();
        assertThat(consumedEventRepository.findById(eventId).orElseThrow().getErrorDetail())
                .contains("text");
    }

    @Test
    void failedResultEventMarksAssetAndJobFailedSafely() {
        UUID processingRequestEventId = UUID.randomUUID();
        Asset asset = persistedAsset(AssetStatus.PROCESSING, ProcessingJobStatus.RUNNING, processingRequestEventId);
        UUID eventId = UUID.randomUUID();

        ProcessingResultHandleResult result = processingResultEventHandler.handle(
                failedEvent(
                        eventId,
                        asset.getId(),
                        processingRequestEventId,
                        processingRequestEventId,
                        "WHISPER_TIMEOUT",
                        "stack trace hidden"
                )
        );

        assertThat(result.status()).isEqualTo(ConsumedProcessingResultEventStatus.APPLIED);
        assertThat(result.applied()).isTrue();
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getStatus()).isEqualTo(AssetStatus.FAILED);

        ProcessingJob processingJob = processingJobRepository.findByAssetId(asset.getId()).orElseThrow();
        assertThat(processingJob.getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(processingJob.getRawUpstreamTaskState()).isEqualTo("whisper_timeout");
        assertThat(consumedEventRepository.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(ConsumedProcessingResultEventStatus.APPLIED);
        verify(fastApiProcessingClient, never()).getTranscriptArtifactRows(processingRequestEventId.toString());
    }

    @Test
    void malformedOrUnsupportedEventsAreRejectedWithoutConsumptionRecord() {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        assertThatThrownBy(() -> processingResultEventHandler.handle(unsupportedEvent(eventId, assetId)))
                .isInstanceOf(ProcessingResultEventRejectedException.class)
                .hasMessageContaining("Unsupported processing result event type");

        assertThat(consumedEventRepository.findById(eventId)).isEmpty();
    }

    @Test
    void processingRequestIdMustMatchCausationEventId() {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        assertThatThrownBy(() -> processingResultEventHandler.handle(transcriptReadyEvent(
                eventId,
                assetId,
                UUID.randomUUID(),
                UUID.randomUUID()
        )))
                .isInstanceOf(ProcessingResultEventRejectedException.class)
                .hasMessageContaining("processingRequestId must match causationEventId");

        assertThat(consumedEventRepository.findById(eventId)).isEmpty();
    }

    @Test
    void resultWithoutMatchingProcessingRequestEventIdDoesNotMarkAssetReady() {
        UUID storedRequestEventId = UUID.randomUUID();
        UUID resultRequestEventId = UUID.randomUUID();
        Asset asset = persistedAsset(AssetStatus.PROCESSING, ProcessingJobStatus.RUNNING, storedRequestEventId);
        UUID eventId = UUID.randomUUID();

        ProcessingResultHandleResult result = processingResultEventHandler.handle(
                transcriptReadyEvent(eventId, asset.getId(), resultRequestEventId, resultRequestEventId)
        );

        assertThat(result.status()).isEqualTo(ConsumedProcessingResultEventStatus.FAILED);
        assertThat(result.applied()).isFalse();
        assertThat(assetRepository.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(AssetStatus.PROCESSING);
        assertThat(processingJobRepository.findByAssetId(asset.getId()).orElseThrow().getProcessingJobStatus())
                .isEqualTo(ProcessingJobStatus.RUNNING);
        assertThat(transcriptRowSnapshotRepository.findByAssetId(asset.getId())).isEmpty();
        assertThat(consumedEventRepository.findById(eventId).orElseThrow().getErrorDetail())
                .contains("request correlation");
        verify(fastApiProcessingClient, never()).getTranscriptArtifactRows(resultRequestEventId.toString());
    }

    @Test
    void directUploadProcessingJobDoesNotRequireProcessingRequestEventId() {
        ProcessingJob processingJob = new ProcessingJob(
                UUID.randomUUID(),
                FASTAPI_DIRECT_UPLOAD_TASK_ID,
                "video-1",
                ProcessingJobStatus.RUNNING,
                "running"
        );

        assertThat(processingJob.getProcessingRequestEventId()).isNull();
        assertThat(processingJob.getFastapiTaskId()).isEqualTo(FASTAPI_DIRECT_UPLOAD_TASK_ID);
    }

    private Asset persistedAsset(
            AssetStatus assetStatus,
            ProcessingJobStatus processingJobStatus,
            UUID processingRequestEventId
    ) {
        UUID assetId = UUID.randomUUID();
        Workspace workspace = workspaceRepository.save(new Workspace(
                UUID.randomUUID(),
                "Algorithms",
                "user-1",
                false
        ));
        Asset asset = assetRepository.save(new Asset(
                assetId,
                "lecture.mp4",
                "Lecture",
                assetStatus,
                workspace,
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        ));
        ProcessingJob processingJob = new ProcessingJob(
                asset.getId(),
                FASTAPI_DIRECT_UPLOAD_TASK_ID,
                "video-1",
                processingJobStatus,
                "running"
        );
        processingJob.setProcessingRequestEventId(processingRequestEventId);
        processingJobRepository.save(processingJob);
        return asset;
    }

    private FastApiTranscriptRowResponse transcriptRow(String id, int segmentIndex, String text) {
        return new FastApiTranscriptRowResponse(
                id,
                "video-1",
                segmentIndex,
                text,
                "2026-06-21T00:00:00Z"
        );
    }

    private String transcriptReadyEvent(
            UUID eventId,
            UUID assetId,
            UUID causationEventId,
            UUID processingRequestId
    ) {
        return """
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
                """.formatted(eventId, assetId, assetId, causationEventId, Instant.now(), processingRequestId);
    }

    private String failedEvent(
            UUID eventId,
            UUID assetId,
            UUID causationEventId,
            UUID processingRequestId,
            String errorCode,
            String message
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "asset.processing.failed",
                  "eventVersion": 1,
                  "aggregateType": "ASSET",
                  "aggregateId": "%s",
                  "eventKey": "%s",
                  "causationEventId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "processingRequestId": "%s",
                    "errorCode": "%s",
                    "message": "%s"
                  }
                }
                """.formatted(eventId, assetId, assetId, causationEventId, Instant.now(), processingRequestId, errorCode, message);
    }

    private String unsupportedEvent(UUID eventId, UUID assetId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "transcript.partial",
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
                """.formatted(eventId, assetId, assetId, UUID.randomUUID(), Instant.now(), UUID.randomUUID());
    }
}
