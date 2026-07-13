package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssetIndexingEventParser {

    private static final int MAX_FINGERPRINT_LENGTH = 128;

    private final ObjectMapper objectMapper;

    public AssetIndexingEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AssetIndexingEventEnvelope parse(String rawEventJson) {
        JsonNode root = readRoot(rawEventJson);
        UUID eventId = readUuid(root, "eventId");
        String eventType = readRequiredText(root, "eventType");
        int eventVersion = readPositiveInt(root, "eventVersion");
        String aggregateType = readRequiredText(root, "aggregateType");
        UUID aggregateId = readUuid(root, "aggregateId");
        String eventKey = readRequiredText(root, "eventKey");
        Instant occurredAt = readInstant(root, "occurredAt");

        if (!IndexingRequestedEventContract.EVENT_TYPE.equals(eventType)) {
            throw new AssetIndexingEventRejectedException("Unsupported indexing event type: " + eventType);
        }
        if (eventVersion != IndexingRequestedEventContract.EVENT_VERSION) {
            throw new AssetIndexingEventRejectedException("Unsupported indexing event version: " + eventVersion);
        }
        if (!IndexingRequestedEventContract.AGGREGATE_TYPE.equals(aggregateType)) {
            throw new AssetIndexingEventRejectedException("Unsupported indexing aggregate type: " + aggregateType);
        }
        if (!aggregateId.toString().equals(eventKey)) {
            throw new AssetIndexingEventRejectedException("Indexing event key must match aggregateId");
        }

        JsonNode payloadNode = root.path("payload");
        if (!payloadNode.isObject()) {
            throw new AssetIndexingEventRejectedException("Indexing event payload is required");
        }
        UUID payloadAssetId = readUuid(payloadNode, "assetId");
        UUID indexingJobId = readUuid(payloadNode, "indexingJobId");
        String snapshotFingerprint = readRequiredText(payloadNode, "snapshotFingerprint");
        if (!aggregateId.equals(payloadAssetId)) {
            throw new AssetIndexingEventRejectedException("Indexing event payload assetId must match aggregateId");
        }
        if (snapshotFingerprint.length() > MAX_FINGERPRINT_LENGTH) {
            throw new AssetIndexingEventRejectedException("Indexing event snapshot fingerprint exceeded safe length");
        }

        return new AssetIndexingEventEnvelope(
                eventId,
                eventType,
                eventVersion,
                aggregateType,
                aggregateId,
                eventKey,
                occurredAt,
                new IndexingRequestedPayload(payloadAssetId, indexingJobId, snapshotFingerprint)
        );
    }

    private JsonNode readRoot(String rawEventJson) {
        try {
            JsonNode root = objectMapper.readTree(rawEventJson);
            if (!root.isObject()) {
                throw new AssetIndexingEventRejectedException("Indexing event envelope must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new AssetIndexingEventRejectedException("Indexing event was not valid JSON", exception);
        }
    }

    private UUID readUuid(JsonNode node, String fieldName) {
        String value = readRequiredText(node, fieldName);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new AssetIndexingEventRejectedException("Indexing event field was not a valid UUID: " + fieldName);
        }
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (!fieldNode.isTextual() || !StringUtils.hasText(fieldNode.asText())) {
            throw new AssetIndexingEventRejectedException("Indexing event field is required: " + fieldName);
        }
        return fieldNode.asText().trim();
    }

    private int readPositiveInt(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (!fieldNode.canConvertToInt() || fieldNode.asInt() <= 0) {
            throw new AssetIndexingEventRejectedException("Indexing event field must be a positive integer: " + fieldName);
        }
        return fieldNode.asInt();
    }

    private Instant readInstant(JsonNode node, String fieldName) {
        String value = readRequiredText(node, fieldName);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new AssetIndexingEventRejectedException("Indexing event field was not a valid instant: " + fieldName);
        }
    }
}
