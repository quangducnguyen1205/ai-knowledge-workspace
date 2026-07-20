package com.aiknowledgeworkspace.workspacecore.search.application.query;

import java.util.UUID;

public record SearchQuery(String text, UUID workspaceId, UUID assetId) {
}
