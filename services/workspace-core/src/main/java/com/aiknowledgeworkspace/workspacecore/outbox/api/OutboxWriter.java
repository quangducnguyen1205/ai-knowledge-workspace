package com.aiknowledgeworkspace.workspacecore.outbox.api;

import java.util.UUID;

public interface OutboxWriter {
    UUID enqueue(OutboxDraft draft);
}
