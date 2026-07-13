package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.UUID;

public record ExplicitIndexingResult(UUID assetId, int indexedDocumentCount) {
}
