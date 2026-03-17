package com.aiknowledgeworkspace.workspacecore.asset;

import java.time.Instant;
import java.util.UUID;

public record Asset(
        UUID id,
        UUID workspaceId,
        String sourceFilename,
        AssetStatus status,
        Instant createdAt
) {
}
