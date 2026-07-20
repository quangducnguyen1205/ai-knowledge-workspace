package com.aiknowledgeworkspace.workspacecore.search.api;

import java.util.List;
import java.util.UUID;

public interface IndexingRequestUseCase {
    void requestIndexingIfEnabled(UUID assetId, List<IndexingRequestRow> transcriptRows);
}
