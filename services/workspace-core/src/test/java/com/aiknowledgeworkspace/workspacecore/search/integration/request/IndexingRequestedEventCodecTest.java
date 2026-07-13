package com.aiknowledgeworkspace.workspacecore.search.integration.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexingRequestedEventCodecTest {

    private static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JOB_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EVENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void preservesIndexingRequestEnvelopeIdentityAndPayloadContract() throws Exception {
        OutboxDraft draft = new IndexingRequestedEventCodec(objectMapper, () -> EVENT_ID)
                .encode(new IndexingRequestedEventData(ASSET_ID, JOB_ID, "sha256-fixture"));

        assertThat(draft.eventId()).isEqualTo(EVENT_ID);
        assertThat(draft.eventType()).isEqualTo("asset.indexing.requested");
        assertThat(draft.eventVersion()).isEqualTo(1);
        assertThat(draft.aggregateType()).isEqualTo("ASSET");
        assertThat(draft.aggregateId()).isEqualTo(ASSET_ID);
        assertThat(draft.eventKey()).isEqualTo(ASSET_ID.toString());

        JsonNode payload = objectMapper.readTree(draft.payload());
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder("assetId", "indexingJobId", "snapshotFingerprint");
        assertThat(payload).isEqualTo(objectMapper.readTree("""
                {
                  "assetId": "11111111-1111-1111-1111-111111111111",
                  "indexingJobId": "33333333-3333-3333-3333-333333333333",
                  "snapshotFingerprint": "sha256-fixture"
                }
                """));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
