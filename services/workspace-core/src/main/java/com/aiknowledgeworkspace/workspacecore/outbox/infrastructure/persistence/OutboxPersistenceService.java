package com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxPersistenceService implements OutboxWriter {

    private final OutboxEventRepository repository;

    public OutboxPersistenceService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public UUID enqueue(OutboxDraft draft) {
        OutboxEvent event = repository.save(OutboxEvent.fromDraft(draft));
        return event.getId();
    }
}
