package com.aiknowledgeworkspace.workspacecore.workspace;

import java.time.Instant;
import java.util.UUID;

public record Workspace(
        UUID id,
        UUID ownerUserId,
        String name,
        Instant createdAt
) {
}
