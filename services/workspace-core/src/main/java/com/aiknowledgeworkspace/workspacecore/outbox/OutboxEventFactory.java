package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventFactory {

    public static final String ASSET_PROCESSING_REQUESTED = "asset.processing.requested";
    public static final int ASSET_PROCESSING_REQUESTED_VERSION = 1;
    public static final String ASSET_AGGREGATE_TYPE = "Asset";

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent assetProcessingRequested(Asset asset, Workspace workspace, StoredObject storedObject) {
        AssetProcessingRequestedPayload payload = new AssetProcessingRequestedPayload(
                asset.getId(),
                workspace.getId(),
                workspace.getOwnerId(),
                storedObject.bucket(),
                storedObject.objectKey(),
                asset.getOriginalFilename(),
                storedObject.contentType(),
                storedObject.sizeBytes(),
                Instant.now()
        );

        return new OutboxEvent(
                ASSET_PROCESSING_REQUESTED,
                ASSET_PROCESSING_REQUESTED_VERSION,
                ASSET_AGGREGATE_TYPE,
                asset.getId(),
                asset.getId().toString(),
                serialize(payload)
        );
    }

    private String serialize(AssetProcessingRequestedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox event payload", exception);
        }
    }
}
