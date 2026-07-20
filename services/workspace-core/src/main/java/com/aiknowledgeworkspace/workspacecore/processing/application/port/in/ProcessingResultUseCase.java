package com.aiknowledgeworkspace.workspacecore.processing.application.port.in;

import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultHandleResult;
import java.util.UUID;

public interface ProcessingResultUseCase {

    ProcessingResultHandleResult handle(String rawEventJson);

    ProcessingResultHandleResult recoverFailedEvent(UUID eventId);
}
