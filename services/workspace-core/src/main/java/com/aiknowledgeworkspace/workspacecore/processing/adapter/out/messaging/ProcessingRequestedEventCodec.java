package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingRequestEventFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessingRequestedEventCodec implements ProcessingRequestEventFactory {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Supplier<UUID> eventIdSupplier;

    @Autowired
    public ProcessingRequestedEventCodec(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC(), UUID::randomUUID);
    }

    ProcessingRequestedEventCodec(ObjectMapper objectMapper, Clock clock, Supplier<UUID> eventIdSupplier) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.eventIdSupplier = eventIdSupplier;
    }

    @Override
    public OutboxDraft create(ProcessingRequestCommand command) {
        return encode(new ProcessingRequestedEventData(
                command.assetId(),
                command.workspaceId(),
                command.ownerId(),
                command.storageBucket(),
                command.objectKey(),
                command.originalFilename(),
                command.contentType(),
                command.sizeBytes()
        ));
    }

    public OutboxDraft encode(ProcessingRequestedEventData data) {
        ProcessingRequestedPayload payload = new ProcessingRequestedPayload(
                data.assetId(),
                data.workspaceId(),
                data.ownerId(),
                data.storageBucket(),
                data.objectKey(),
                data.originalFilename(),
                data.contentType(),
                data.sizeBytes(),
                Instant.now(clock)
        );
        return new OutboxDraft(
                eventIdSupplier.get(),
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
                data.assetId(),
                data.assetId().toString(),
                serialize(payload)
        );
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox event payload", exception);
        }
    }

    private record ProcessingRequestedPayload(
            UUID assetId,
            UUID workspaceId,
            String ownerId,
            String storageBucket,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Instant requestedAt
    ) {
    }
}
