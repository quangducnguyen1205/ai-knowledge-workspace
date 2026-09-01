package com.aiknowledgeworkspace.workspacecore.processing;

import com.aiknowledgeworkspace.workspacecore.processing.application.service.ProcessingRequestApplicationService;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingRequestEventFactory;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ProcessingRequestApplicationServiceTest {

    private final ProcessingJobStore repository = mock(ProcessingJobStore.class);
    private final ProcessingRequestEventFactory eventFactory = mock(ProcessingRequestEventFactory.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final ProcessingRequestApplicationService service =
            new ProcessingRequestApplicationService(repository, eventFactory, outboxWriter);

    @Test
    void createsKafkaJobBeforeEnqueueUsingFactoryOwnedEventIdentity() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxDraft draft = new OutboxDraft(
                eventId, "asset.processing.requested", 1, "Asset", assetId, assetId.toString(), "{}"
        );
        when(eventFactory.create(any(ProcessingRequestCommand.class))).thenReturn(draft);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingRequestCommand command = new ProcessingRequestCommand(
                assetId, workspaceId, "user-1", "bucket", "object", "lesson.mp4", "video/mp4", 12L
        );
        service.createKafkaJobAndRequest(command);

        ArgumentCaptor<ProcessingJob> job = ArgumentCaptor.forClass(ProcessingJob.class);
        verify(eventFactory).create(command);
        var ordered = inOrder(repository, outboxWriter);
        ordered.verify(repository).save(job.capture());
        ordered.verify(outboxWriter).enqueue(draft);
        assertThat(job.getValue().getProcessingRequestEventId()).isEqualTo(eventId);
        assertThat(job.getValue().getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(job.getValue().getRawUpstreamTaskState()).isEqualTo("processing_request_pending");
    }

    @Test
    void createsYouTubeJobWithTheExplicitV2Command() {
        UUID assetId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        YouTubeProcessingRequestCommand command = new YouTubeProcessingRequestCommand(
                assetId, UUID.randomUUID(), "owner-1", "abc_DEF-123"
        );
        OutboxDraft draft = new OutboxDraft(
                eventId, "asset.processing.requested", 2, "ASSET", assetId, assetId.toString(), "{}"
        );
        when(eventFactory.create(command)).thenReturn(draft);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createYouTubeKafkaJobAndRequest(command);

        ArgumentCaptor<ProcessingJob> job = ArgumentCaptor.forClass(ProcessingJob.class);
        verify(eventFactory).create(command);
        var ordered = inOrder(repository, outboxWriter);
        ordered.verify(repository).save(job.capture());
        ordered.verify(outboxWriter).enqueue(draft);
        assertThat(job.getValue().getProcessingRequestEventId()).isEqualTo(eventId);
        assertThat(job.getValue().getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
    }

    @Test
    void retryReusesTheOneJobButAssignsAFreshUploadRequestIdentity() {
        UUID assetId = UUID.randomUUID();
        UUID oldEventId = UUID.randomUUID();
        UUID newEventId = UUID.randomUUID();
        ProcessingJob existing = new ProcessingJob(assetId, ProcessingJobStatus.FAILED, "whisper_timeout");
        existing.setProcessingRequestEventId(oldEventId);
        ProcessingRequestCommand command = new ProcessingRequestCommand(
                assetId, UUID.randomUUID(), "owner-1", "bucket", "object", "lesson.mp4", "video/mp4", 12L
        );
        OutboxDraft draft = new OutboxDraft(
                newEventId, "asset.processing.requested", 1, "Asset", assetId, assetId.toString(), "{}"
        );
        when(repository.findByAssetId(assetId)).thenReturn(Optional.of(existing));
        when(eventFactory.create(command)).thenReturn(draft);
        when(repository.save(existing)).thenReturn(existing);

        service.retryKafkaJobAndRequest(command);

        assertThat(existing.getProcessingRequestEventId()).isEqualTo(newEventId).isNotEqualTo(oldEventId);
        assertThat(existing.getProcessingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(existing.getRawUpstreamTaskState()).isEqualTo("processing_request_pending");
        var ordered = inOrder(repository, outboxWriter);
        ordered.verify(repository).save(existing);
        ordered.verify(outboxWriter).enqueue(draft);
    }

    @Test
    void createAndRetryLogCarryAssetJobAndRequestEventIdentifiers(CapturedOutput output) {
        UUID assetId = UUID.randomUUID();
        UUID createEventId = UUID.randomUUID();
        UUID retryEventId = UUID.randomUUID();
        ProcessingRequestCommand command = new ProcessingRequestCommand(
                assetId, UUID.randomUUID(), "user-1", "bucket", "object", "lesson.mp4", "video/mp4", 12L
        );
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventFactory.create(command)).thenReturn(new OutboxDraft(
                createEventId, "asset.processing.requested", 1, "Asset", assetId, assetId.toString(), "{}"
        ));

        service.createKafkaJobAndRequest(command);

        assertThat(output.getAll())
                .contains("Processing request created assetId=" + assetId)
                .contains("requestEventId=" + createEventId);

        ProcessingJob existing = new ProcessingJob(assetId, ProcessingJobStatus.FAILED, "whisper_timeout");
        existing.setProcessingRequestEventId(createEventId);
        when(repository.findByAssetId(assetId)).thenReturn(Optional.of(existing));
        when(eventFactory.create(command)).thenReturn(new OutboxDraft(
                retryEventId, "asset.processing.requested", 1, "Asset", assetId, assetId.toString(), "{}"
        ));

        service.retryKafkaJobAndRequest(command);

        assertThat(output.getAll())
                .contains("Processing request retried assetId=" + assetId)
                .contains("requestEventId=" + retryEventId);
    }

    @Test
    void retryUsesTheExplicitYouTubeV2FactoryPath() {
        UUID assetId = UUID.randomUUID();
        ProcessingJob existing = new ProcessingJob(assetId, ProcessingJobStatus.FAILED, "youtube_unavailable");
        existing.setProcessingRequestEventId(UUID.randomUUID());
        YouTubeProcessingRequestCommand command = new YouTubeProcessingRequestCommand(
                assetId, UUID.randomUUID(), "owner-1", "abc_DEF-123"
        );
        OutboxDraft draft = new OutboxDraft(
                UUID.randomUUID(), "asset.processing.requested", 2, "ASSET", assetId, assetId.toString(), "{}"
        );
        when(repository.findByAssetId(assetId)).thenReturn(Optional.of(existing));
        when(eventFactory.create(command)).thenReturn(draft);
        when(repository.save(existing)).thenReturn(existing);

        service.retryYouTubeKafkaJobAndRequest(command);

        verify(eventFactory).create(command);
        verify(outboxWriter).enqueue(draft);
        assertThat(existing.getProcessingRequestEventId()).isEqualTo(draft.eventId());
    }
}
