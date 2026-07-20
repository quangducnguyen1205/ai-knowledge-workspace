package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging.ProcessingRequestedEventData;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging.ProcessingRequestedEventCodec;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingRequestedEventCodecTest {

    private static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKSPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-01T10:15:30Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void preservesProcessingRequestEnvelopeIdentityAndPayloadContract() throws Exception {
        OutboxDraft draft = codec().encode(new ProcessingRequestedEventData(
                ASSET_ID,
                WORKSPACE_ID,
                "learner-1",
                "workspace-media",
                "users/learner-1/workspaces/learning/assets/lesson/raw/lesson.mp4",
                "lesson.mp4",
                "video/mp4",
                4096L
        ));

        assertThat(draft.eventId()).isEqualTo(EVENT_ID);
        assertThat(draft.eventType()).isEqualTo("asset.processing.requested");
        assertThat(draft.eventVersion()).isEqualTo(1);
        assertThat(draft.aggregateType()).isEqualTo("Asset");
        assertThat(draft.aggregateId()).isEqualTo(ASSET_ID);
        assertThat(draft.eventKey()).isEqualTo(ASSET_ID.toString());

        JsonNode payload = objectMapper.readTree(draft.payload());
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder(
                "assetId", "workspaceId", "ownerId", "storageBucket", "objectKey",
                "originalFilename", "contentType", "sizeBytes", "requestedAt"
        );
        assertThat(payload.path("assetId").asText()).isEqualTo(ASSET_ID.toString());
        assertThat(payload.path("workspaceId").asText()).isEqualTo(WORKSPACE_ID.toString());
        assertThat(payload.path("ownerId").asText()).isEqualTo("learner-1");
        assertThat(payload.path("storageBucket").asText()).isEqualTo("workspace-media");
        assertThat(payload.path("objectKey").asText())
                .isEqualTo("users/learner-1/workspaces/learning/assets/lesson/raw/lesson.mp4");
        assertThat(payload.path("originalFilename").asText()).isEqualTo("lesson.mp4");
        assertThat(payload.path("contentType").asText()).isEqualTo("video/mp4");
        assertThat(payload.path("sizeBytes").asLong()).isEqualTo(4096L);
        assertThat(payload.path("requestedAt").isNumber()).isTrue();
        assertThat(payload.path("requestedAt").asDouble()).isEqualTo((double) REQUESTED_AT.getEpochSecond());
    }

    @Test
    void preservesNullSerializationForOptionalPayloadValues() throws Exception {
        OutboxDraft draft = codec().encode(new ProcessingRequestedEventData(
                ASSET_ID, WORKSPACE_ID, null, "bucket", "object", null, null, 0L
        ));

        JsonNode payload = objectMapper.readTree(draft.payload());
        assertThat(payload.has("ownerId")).isTrue();
        assertThat(payload.path("ownerId").isNull()).isTrue();
        assertThat(payload.has("originalFilename")).isTrue();
        assertThat(payload.path("originalFilename").isNull()).isTrue();
        assertThat(payload.has("contentType")).isTrue();
        assertThat(payload.path("contentType").isNull()).isTrue();
    }

    private ProcessingRequestedEventCodec codec() {
        return new ProcessingRequestedEventCodec(
                objectMapper,
                Clock.fixed(REQUESTED_AT, ZoneOffset.UTC),
                () -> EVENT_ID
        );
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
