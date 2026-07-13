package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.List;
import java.util.UUID;

public interface IndexingRequestApplication {
    void requestIndexingIfEnabled(UUID assetId, List<IndexingRequestRow> transcriptRows);
}
