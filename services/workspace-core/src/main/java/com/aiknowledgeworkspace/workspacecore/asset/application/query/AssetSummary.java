package com.aiknowledgeworkspace.workspacecore.asset.application.query;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import java.time.Instant;
import java.util.UUID;

public record AssetSummary(UUID id, String title, AssetStatus status, UUID workspaceId, Instant createdAt) {
}
