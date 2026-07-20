package com.aiknowledgeworkspace.workspacecore.outbox.api;

public interface OutboxManualRecovery {
    OutboxDeliveryStatus requeueStuckPublishing(StuckOutboxRecoveryRequest request);
}
