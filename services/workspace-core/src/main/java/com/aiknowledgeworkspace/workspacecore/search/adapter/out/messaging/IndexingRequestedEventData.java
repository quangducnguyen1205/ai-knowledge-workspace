package com.aiknowledgeworkspace.workspacecore.search.adapter.out.messaging;

import java.util.UUID;

public record IndexingRequestedEventData(UUID assetId, UUID indexingJobId, String snapshotFingerprint) {
}
