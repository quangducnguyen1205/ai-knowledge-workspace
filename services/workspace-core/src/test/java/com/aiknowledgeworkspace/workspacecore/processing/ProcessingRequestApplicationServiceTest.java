package com.aiknowledgeworkspace.workspacecore.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.processing.application.DirectProcessingJobCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.KafkaProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventCodec;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessingRequestApplicationServiceTest {

    private final ProcessingJobRepository repository = mock(ProcessingJobRepository.class);
    private final ProcessingProperties properties = new ProcessingProperties();
    private final ProcessingRequestedEventCodec codec = mock(ProcessingRequestedEventCodec.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final ProcessingRequestApplicationService service =
            new ProcessingRequestApplicationService(repository, properties, codec, outboxWriter);

    @Test
    void createsDirectJobWithUnchangedProviderCorrelation() {
        UUID assetId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingJobView result = service.createDirectJob(new DirectProcessingJobCommand(
                assetId, "task-1", "video-1", ProcessingJobStatus.PENDING, "pending"
        ));

        ArgumentCaptor<ProcessingJob> job = ArgumentCaptor.forClass(ProcessingJob.class);
        verify(repository).save(job.capture());
        assertThat(job.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(job.getValue().getFastapiTaskId()).isEqualTo("task-1");
        assertThat(job.getValue().getFastapiVideoId()).isEqualTo("video-1");
        assertThat(job.getValue().getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(job.getValue().getProcessingRequestEventId()).isNull();
        assertThat(result.assetId()).isEqualTo(assetId);
    }

    @Test
    void createsKafkaJobBeforeEnqueueUsingCodecOwnedEventIdentity() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxDraft draft = new OutboxDraft(
                eventId, "asset.processing.requested", 1, "Asset", assetId, assetId.toString(), "{}"
        );
        when(codec.encode(any())).thenReturn(draft);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createKafkaJobAndRequest(new KafkaProcessingRequestCommand(
                assetId, workspaceId, "user-1", "bucket", "object", "lesson.mp4", "video/mp4", 12L
        ));

        ArgumentCaptor<ProcessingRequestedEventData> eventData =
                ArgumentCaptor.forClass(ProcessingRequestedEventData.class);
        ArgumentCaptor<ProcessingJob> job = ArgumentCaptor.forClass(ProcessingJob.class);
        verify(codec).encode(eventData.capture());
        assertThat(eventData.getValue()).isEqualTo(new ProcessingRequestedEventData(
                assetId, workspaceId, "user-1", "bucket", "object", "lesson.mp4", "video/mp4", 12L
        ));
        var ordered = inOrder(repository, outboxWriter);
        ordered.verify(repository).save(job.capture());
        ordered.verify(outboxWriter).enqueue(draft);
        assertThat(job.getValue().getProcessingRequestEventId()).isEqualTo(eventId);
        assertThat(job.getValue().getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(job.getValue().getRawUpstreamTaskState()).isEqualTo("kafka_request_pending");
    }

    @Test
    void reportsConfiguredTriggerModeWithoutExposingProperties() {
        assertThat(service.usesKafkaRequestMode()).isFalse();
        properties.setTriggerMode(ProcessingTriggerMode.KAFKA_REQUEST);
        assertThat(service.usesKafkaRequestMode()).isTrue();
    }
}
