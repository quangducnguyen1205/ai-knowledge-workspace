package com.aiknowledgeworkspace.workspacecore.search.api;

import java.util.UUID;

public record ExplicitIndexingResult(UUID assetId, int indexedDocumentCount) {
}
