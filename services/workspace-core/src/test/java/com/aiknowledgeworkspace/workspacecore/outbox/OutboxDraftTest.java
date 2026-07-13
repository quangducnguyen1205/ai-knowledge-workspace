package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxDraftTest {

    @Test
    void rejectsMissingGenericEnvelopeValues() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new OutboxDraft(id, " ", 1, "Asset", id, id.toString(), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventType is required");
        assertThatThrownBy(() -> new OutboxDraft(id, "event", 0, "Asset", id, id.toString(), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventVersion must be positive");
        assertThatThrownBy(() -> new OutboxDraft(id, "event", 1, "Asset", id, id.toString(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload is required");
    }
}
