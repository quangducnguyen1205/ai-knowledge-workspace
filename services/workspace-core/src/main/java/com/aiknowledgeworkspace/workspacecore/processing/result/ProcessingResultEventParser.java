package com.aiknowledgeworkspace.workspacecore.processing.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
