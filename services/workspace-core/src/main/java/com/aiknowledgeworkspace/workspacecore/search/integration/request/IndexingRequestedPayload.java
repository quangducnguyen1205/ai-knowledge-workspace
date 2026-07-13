package com.aiknowledgeworkspace.workspacecore.search.integration.request;

import java.util.UUID;

public record IndexingRequestedPayload(UUID assetId, UUID indexingJobId, String snapshotFingerprint) {
}
