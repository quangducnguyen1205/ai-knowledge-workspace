package com.aiknowledgeworkspace.workspacecore.search.application.port.out.messaging;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import java.util.UUID;

public interface IndexingRequestEventFactory {

    OutboxDraft create(UUID assetId, UUID indexingJobId, String snapshotFingerprint);
}
