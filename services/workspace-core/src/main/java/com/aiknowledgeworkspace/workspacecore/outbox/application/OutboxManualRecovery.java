package com.aiknowledgeworkspace.workspacecore.outbox.application;

public interface OutboxManualRecovery {
    OutboxDeliveryStatus requeueStuckPublishing(StuckOutboxRecoveryRequest request);
}
