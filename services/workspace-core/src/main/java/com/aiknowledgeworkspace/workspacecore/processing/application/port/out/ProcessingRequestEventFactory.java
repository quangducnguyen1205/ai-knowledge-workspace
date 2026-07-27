package com.aiknowledgeworkspace.workspacecore.processing.application.port.out;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;

public interface ProcessingRequestEventFactory {

    OutboxDraft create(ProcessingRequestCommand command);

    OutboxDraft create(YouTubeProcessingRequestCommand command);
}
