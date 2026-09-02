package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging.ProcessingRequestedEventData;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging.ProcessingRequestedEventCodec;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;

/**
 * The bytes DemoFastAPI parses are produced by the application-managed ObjectMapper, so this test
 * serializes with that same bean rather than one it configures itself. A locally built mapper
 * writes an Instant as an epoch number; the application's mapper writes ISO-8601, and the
 * consumer's payload model declares requestedAt as a string. Asserting against a private mapper
 * would therefore pin a representation the consumer rejects, which is why the canonical fixtures
 * below are compared byte for byte and shared with the DemoFastAPI parser test.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:processing-requested-codec;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ProcessingRequestedEventCodecTest {

    private static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKSPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-01T10:15:30Z");

    @Autowired
    private ObjectMapper objectMapper;

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
    }

    @Test
    void serializesRequestedAtAsTheStringTheConsumerPayloadModelDeclares() throws Exception {
        OutboxDraft draft = codec().encode(new ProcessingRequestedEventData(
                ASSET_ID, WORKSPACE_ID, "learner-1", "workspace-media", "objects/lesson.mp4",
                "lesson.mp4", "video/mp4", 4096L
        ));

        JsonNode payload = objectMapper.readTree(draft.payload());
        assertThat(payload.path("requestedAt").isTextual())
                .withFailMessage(
                        "DemoFastAPI declares requestedAt as a string; an epoch number is rejected "
                                + "by the consumer payload model. Actual node: %s",
                        payload.path("requestedAt"))
                .isTrue();
        assertThat(payload.path("requestedAt").asText()).isEqualTo(REQUESTED_AT.toString());
    }

    @Test
    void v1PayloadMatchesTheCanonicalConsumerFixtureByteForByte() throws Exception {
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

        assertThat(draft.payload()).isEqualTo(fixture("processing-requested-v1.json"));
    }

    @Test
    void v2PayloadMatchesTheCanonicalConsumerFixtureByteForByte() throws Exception {
        OutboxDraft draft = codec().create(new YouTubeProcessingRequestCommand(
                ASSET_ID,
                WORKSPACE_ID,
                "learner-1",
                "abc_DEF-123"
        ));

        assertThat(draft.payload()).isEqualTo(fixture("processing-requested-v2.json"));
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

    @Test
    void producesTheExactFastApiSupportedYouTubeV2Draft() throws Exception {
        OutboxDraft draft = codec().create(new YouTubeProcessingRequestCommand(
                ASSET_ID,
                WORKSPACE_ID,
                "learner-1",
                "abc_DEF-123"
        ));

        assertThat(draft.eventId()).isEqualTo(EVENT_ID);
        assertThat(draft.eventType()).isEqualTo("asset.processing.requested");
        assertThat(draft.eventVersion()).isEqualTo(2);
        assertThat(draft.aggregateType()).isEqualTo("ASSET");
        assertThat(draft.aggregateId()).isEqualTo(ASSET_ID);
        assertThat(draft.eventKey()).isEqualTo(ASSET_ID.toString());

        JsonNode payload = objectMapper.readTree(draft.payload());
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder(
                "assetId",
                "workspaceId",
                "ownerId",
                "sourceType",
                "youtubeVideoId",
                "requestedAt"
        );
        assertThat(payload.path("assetId").asText()).isEqualTo(ASSET_ID.toString());
        assertThat(payload.path("workspaceId").asText()).isEqualTo(WORKSPACE_ID.toString());
        assertThat(payload.path("ownerId").asText()).isEqualTo("learner-1");
        assertThat(payload.path("sourceType").asText()).isEqualTo("YOUTUBE");
        assertThat(payload.path("youtubeVideoId").asText()).isEqualTo("abc_DEF-123");
        assertThat(payload.path("requestedAt").asText()).isEqualTo(REQUESTED_AT.toString());
    }

    private ProcessingRequestedEventCodec codec() {
        return new ProcessingRequestedEventCodec(
                objectMapper,
                Clock.fixed(REQUESTED_AT, ZoneOffset.UTC),
                () -> EVENT_ID
        );
    }

    private String fixture(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/contract/" + name)) {
            assertThat(stream).withFailMessage("missing canonical fixture %s", name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
