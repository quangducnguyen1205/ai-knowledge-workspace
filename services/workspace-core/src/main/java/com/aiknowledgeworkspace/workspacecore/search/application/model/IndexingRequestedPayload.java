package com.aiknowledgeworkspace.workspacecore.search.application.model;

import java.util.UUID;

public record IndexingRequestedPayload(UUID assetId, UUID indexingJobId, String snapshotFingerprint) {
}
