package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetIndexingEventParserTest {

    private final AssetIndexingEventParser parser = new AssetIndexingEventParser(
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void parsesValidIndexingEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID indexingJobId = UUID.randomUUID();

        AssetIndexingEventEnvelope event = parser.parse(envelope(
                eventId,
                assetId,
                indexingJobId,
                "abc123",
                IndexingRequestedEventContract.EVENT_TYPE,
                1,
                assetId.toString()
        ));

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo(IndexingRequestedEventContract.EVENT_TYPE);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateType()).isEqualTo(IndexingRequestedEventContract.AGGREGATE_TYPE);
        assertThat(event.aggregateId()).isEqualTo(assetId);
        assertThat(event.eventKey()).isEqualTo(assetId.toString());
        assertThat(event.payload().assetId()).isEqualTo(assetId);
        assertThat(event.payload().indexingJobId()).isEqualTo(indexingJobId);
        assertThat(event.payload().snapshotFingerprint()).isEqualTo("abc123");
    }

    @Test
    void rejectsUnsupportedEventType() {
        assertThatThrownBy(() -> parser.parse(envelope(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "abc123",
                "asset.processing.requested",
                1,
                UUID.randomUUID().toString()
        )))
                .isInstanceOf(AssetIndexingEventRejectedException.class)
                .hasMessageContaining("Unsupported indexing event type");
    }

    @Test
    void rejectsUnsupportedVersion() {
        UUID assetId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(envelope(
                UUID.randomUUID(),
                assetId,
                UUID.randomUUID(),
                "abc123",
                IndexingRequestedEventContract.EVENT_TYPE,
                2,
                assetId.toString()
        )))
                .isInstanceOf(AssetIndexingEventRejectedException.class)
                .hasMessageContaining("Unsupported indexing event version");
    }

    @Test
    void rejectsEventKeyMismatch() {
        assertThatThrownBy(() -> parser.parse(envelope(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "abc123",
                IndexingRequestedEventContract.EVENT_TYPE,
                1,
                UUID.randomUUID().toString()
        )))
                .isInstanceOf(AssetIndexingEventRejectedException.class)
                .hasMessageContaining("key must match aggregateId");
    }

    @Test
    void rejectsOverlongFingerprint() {
        UUID assetId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(envelope(
                UUID.randomUUID(),
                assetId,
                UUID.randomUUID(),
                "x".repeat(129),
                IndexingRequestedEventContract.EVENT_TYPE,
                1,
                assetId.toString()
        )))
                .isInstanceOf(AssetIndexingEventRejectedException.class)
                .hasMessageContaining("snapshot fingerprint exceeded safe length");
    }

    private String envelope(
            UUID eventId,
            UUID assetId,
            UUID indexingJobId,
            String snapshotFingerprint,
            String eventType,
            int eventVersion,
            String eventKey
    ) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": %d,
                  "aggregateType": "ASSET",
                  "aggregateId": "%s",
                  "eventKey": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "assetId": "%s",
                    "indexingJobId": "%s",
                    "snapshotFingerprint": "%s"
                  }
                }
                """.formatted(
                eventId,
                eventType,
                eventVersion,
                assetId,
                eventKey,
                Instant.parse("2026-06-22T00:00:00Z"),
                assetId,
                indexingJobId,
                snapshotFingerprint
        );
    }
}
