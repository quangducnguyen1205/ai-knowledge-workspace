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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(eventFactory.create(any())).thenReturn(draft);
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
}
