package com.aiknowledgeworkspace.workspacecore.processing.application.port.out;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEvent;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingResultEventStore {

    Optional<ConsumedProcessingResultEvent> findEventById(UUID eventId);

    ConsumedProcessingResultEvent save(ConsumedProcessingResultEvent event);
}
