package com.aiknowledgeworkspace.workspacecore.outbox;

import java.util.UUID;

public record AssetIndexingRequestedPayload(
        UUID assetId,
        UUID indexingJobId,
        String snapshotFingerprint
) {
}
