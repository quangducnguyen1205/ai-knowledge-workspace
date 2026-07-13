package com.aiknowledgeworkspace.workspacecore.search.integration.request;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IndexingRequestedEventCodec {

    private final ObjectMapper objectMapper;
    private final Supplier<UUID> eventIdSupplier;

    @Autowired
    public IndexingRequestedEventCodec(ObjectMapper objectMapper) {
        this(objectMapper, UUID::randomUUID);
    }

    IndexingRequestedEventCodec(ObjectMapper objectMapper, Supplier<UUID> eventIdSupplier) {
        this.objectMapper = objectMapper;
        this.eventIdSupplier = eventIdSupplier;
    }

    public OutboxDraft encode(IndexingRequestedEventData data) {
        IndexingRequestedPayload payload = new IndexingRequestedPayload(
                data.assetId(),
                data.indexingJobId(),
                data.snapshotFingerprint()
        );
        return new OutboxDraft(
                eventIdSupplier.get(),
                IndexingRequestedEventContract.EVENT_TYPE,
                IndexingRequestedEventContract.EVENT_VERSION,
                IndexingRequestedEventContract.AGGREGATE_TYPE,
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
}
