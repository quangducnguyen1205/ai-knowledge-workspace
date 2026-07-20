package com.aiknowledgeworkspace.workspacecore.search.api;

import java.util.UUID;

public interface ExplicitIndexingUseCase {
    ExplicitIndexingResult indexAssetTranscript(UUID assetId);
}
