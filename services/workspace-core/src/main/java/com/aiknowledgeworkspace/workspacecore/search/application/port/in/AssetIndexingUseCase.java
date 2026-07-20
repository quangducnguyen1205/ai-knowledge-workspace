package com.aiknowledgeworkspace.workspacecore.search.application.port.in;

import com.aiknowledgeworkspace.workspacecore.search.application.result.AssetIndexingHandleResult;

public interface AssetIndexingUseCase {

    AssetIndexingHandleResult handle(String rawEventJson);
}
