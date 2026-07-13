package com.aiknowledgeworkspace.workspacecore.outbox.application;

public interface OutboxRelay {
    RelayOutcome relay(RelayRequest request);
}
