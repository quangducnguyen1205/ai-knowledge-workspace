package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.UUID;

public interface ExplicitIndexingApplication {
    ExplicitIndexingResult indexAssetTranscript(UUID assetId);
}
