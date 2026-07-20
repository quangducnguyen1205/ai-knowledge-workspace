package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultEventRejectedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ProcessingResultEventParser {

    static final String TOPIC = "asset.processing.result.v1";
    static final String TRANSCRIPT_READY = "transcript.ready";
    static final String ASSET_PROCESSING_FAILED = "asset.processing.failed";
    static final int EVENT_VERSION = 1;
    static final String ASSET_AGGREGATE_TYPE = "ASSET";

    private final ObjectMapper objectMapper;

    ProcessingResultEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ProcessingResultEventEnvelope parse(String rawEventJson) {
        JsonNode root = readRoot(rawEventJson);
        UUID eventId = requiredUuid(root, "eventId");
        String eventType = requiredText(root, "eventType");
        int eventVersion = requiredPositiveInt(root, "eventVersion");
        String aggregateType = requiredText(root, "aggregateType");
        UUID aggregateId = requiredUuid(root, "aggregateId");
        String eventKey = requiredText(root, "eventKey");
        UUID eventKeyAsAssetId = parseUuid(eventKey, "eventKey");
        UUID causationEventId = requiredUuid(root, "causationEventId");
        Instant occurredAt = requiredInstant(root, "occurredAt");
        JsonNode payload = requiredObject(root, "payload");

        if (eventVersion != EVENT_VERSION) {
            throw rejected("Unsupported processing result event version: " + eventVersion);
        }
        if (!ASSET_AGGREGATE_TYPE.equals(aggregateType)) {
            throw rejected("Unsupported processing result aggregate type: " + aggregateType);
        }
        if (!aggregateId.equals(eventKeyAsAssetId)) {
            throw rejected("Processing result aggregateId must match eventKey");
        }

        ProcessingResultPayload parsedPayload = parsePayload(eventType, payload);
        if (!causationEventId.equals(parsedPayload.processingRequestId())) {
            throw rejected("Processing result processingRequestId must match causationEventId");
        }

        return new ProcessingResultEventEnvelope(
                eventId,
                eventType,
                eventVersion,
                aggregateType,
                aggregateId,
                eventKey,
                causationEventId,
                occurredAt,
                parsedPayload
        );
    }

    String recoverableEnvelopeJson(String rawEventJson, ProcessingResultEventEnvelope event) {
        JsonNode payload = requiredObject(readRoot(rawEventJson), "payload");
        ObjectNode recoverablePayload = objectMapper.createObjectNode();
        copyAllowedPayloadFields(event.eventType(), payload, recoverablePayload, event.payload());

        ObjectNode recoverableEnvelope = objectMapper.createObjectNode();
        recoverableEnvelope.put("eventId", event.eventId().toString());
        recoverableEnvelope.put("eventType", event.eventType());
        recoverableEnvelope.put("eventVersion", event.eventVersion());
        recoverableEnvelope.put("aggregateType", event.aggregateType());
        recoverableEnvelope.put("aggregateId", event.aggregateId().toString());
        recoverableEnvelope.put("eventKey", event.eventKey());
        recoverableEnvelope.put("causationEventId", event.causationEventId().toString());
        recoverableEnvelope.put("occurredAt", event.occurredAt().toString());
        recoverableEnvelope.set("payload", recoverablePayload);

        try {
            return objectMapper.writeValueAsString(recoverableEnvelope);
        } catch (JsonProcessingException exception) {
            throw new ProcessingResultEventRejectedException(
                    "Processing result event could not be converted to recoverable JSON",
                    exception
            );
        }
    }

    private ProcessingResultPayload parsePayload(String eventType, JsonNode payload) {
        return switch (eventType) {
            case TRANSCRIPT_READY -> new TranscriptReadyPayload(requiredPayloadUuid(
                    payload,
                    "processingRequestId",
                    "processing_request_id"
            ));
            case ASSET_PROCESSING_FAILED -> new AssetProcessingFailedPayload(
                    requiredPayloadUuid(payload, "processingRequestId", "processing_request_id"),
                    optionalPayloadText(payload, "errorCode", "error_code"),
                    optionalPayloadText(payload, "message", "errorMessage", "error_message")
            );
            default -> throw rejected("Unsupported processing result event type: " + eventType);
        };
    }

    private void copyAllowedPayloadFields(
            String eventType,
            JsonNode source,
            ObjectNode target,
            ProcessingResultPayload parsedPayload
    ) {
        target.put("processingRequestId", parsedPayload.processingRequestId().toString());

        switch (eventType) {
            case TRANSCRIPT_READY -> {
                copyOptionalScalar(source, target, "assetId", 64, "asset_id");
                copyOptionalScalar(source, target, "status", 64);
                copyOptionalScalar(source, target, "segmentCount", 32);
                copyOptionalScalar(source, target, "completedAt", 128);
            }
            case ASSET_PROCESSING_FAILED -> {
                copyOptionalScalar(source, target, "assetId", 64, "asset_id");
                copyOptionalScalar(source, target, "status", 64);
                copyOptionalScalar(source, target, "errorCode", 128, "error_code");
                copyOptionalScalar(source, target, "errorMessage", 1024, "message", "error_message");
                copyOptionalScalar(source, target, "completedAt", 128);
            }
            default -> throw rejected("Unsupported processing result event type: " + eventType);
        }
    }

    private void copyOptionalScalar(JsonNode source, ObjectNode target, String fieldName, int maxTextLength, String... aliases) {
        JsonNode value = source.get(fieldName);
        if (value == null) {
            for (String alias : aliases) {
                value = source.get(alias);
                if (value != null) {
                    break;
                }
            }
        }
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isValueNode() || value.isBinary()) {
            throw rejected("Processing result event payload field '" + fieldName + "' must be a scalar value");
        }
        if (value.isTextual() && value.asText().length() > maxTextLength) {
            throw rejected("Processing result event payload field '" + fieldName + "' exceeded safe length");
        }
        target.set(fieldName, value.deepCopy());
    }

    private JsonNode readRoot(String rawEventJson) {
        if (!StringUtils.hasText(rawEventJson)) {
            throw rejected("Processing result event payload was empty");
        }
        try {
            JsonNode root = objectMapper.readTree(rawEventJson);
            if (root == null || !root.isObject()) {
                throw rejected("Processing result event must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new ProcessingResultEventRejectedException("Processing result event was not valid JSON", exception);
        }
    }

    private JsonNode requiredObject(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isObject()) {
            throw rejected("Processing result event field '" + fieldName + "' must be a JSON object");
        }
        return value;
    }

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw rejected("Processing result event field '" + fieldName + "' is required");
        }
        return value.asText().trim();
    }

    private String requiredPayloadText(JsonNode payload, String... fieldNames) {
        String value = optionalPayloadText(payload, fieldNames);
        if (!StringUtils.hasText(value)) {
            throw rejected("Processing result event payload is missing '" + fieldNames[0] + "'");
        }
        return value;
    }

    private UUID requiredPayloadUuid(JsonNode payload, String... fieldNames) {
        return parseUuid(requiredPayloadText(payload, fieldNames), fieldNames[0]);
    }

    private String optionalPayloadText(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private UUID requiredUuid(JsonNode root, String fieldName) {
        return parseUuid(requiredText(root, fieldName), fieldName);
    }

    private UUID parseUuid(String rawValue, String fieldName) {
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new ProcessingResultEventRejectedException(
                    "Processing result event field '" + fieldName + "' must be a UUID",
                    exception
            );
        }
    }

    private int requiredPositiveInt(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.canConvertToInt() || value.asInt() <= 0) {
            throw rejected("Processing result event field '" + fieldName + "' must be a positive integer");
        }
        return value.asInt();
    }

    private Instant requiredInstant(JsonNode root, String fieldName) {
        String rawValue = requiredText(root, fieldName);
        try {
            return Instant.parse(rawValue);
        } catch (DateTimeParseException exception) {
            throw new ProcessingResultEventRejectedException(
                    "Processing result event field '" + fieldName + "' must be an ISO-8601 instant",
                    exception
            );
        }
    }

    private ProcessingResultEventRejectedException rejected(String message) {
        return new ProcessingResultEventRejectedException(message);
    }
}
