package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingResultEventStore;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProcessingResultInboxPersistenceAdapter implements ProcessingResultEventStore {

    private final ProcessingResultEventJpaRepository resultEventRepository;

    ProcessingResultInboxPersistenceAdapter(ProcessingResultEventJpaRepository resultEventRepository) {
        this.resultEventRepository = resultEventRepository;
    }

    @Override
    public Optional<ConsumedProcessingResultEvent> findEventById(UUID eventId) {
        return resultEventRepository.findById(eventId);
    }

    @Override
    public ConsumedProcessingResultEvent save(ConsumedProcessingResultEvent event) {
        return resultEventRepository.save(event);
    }
}
