package com.aiknowledgeworkspace.workspacecore.outbox.api;

public interface OutboxRelay {
    RelayOutcome relay(RelayRequest request);
}
