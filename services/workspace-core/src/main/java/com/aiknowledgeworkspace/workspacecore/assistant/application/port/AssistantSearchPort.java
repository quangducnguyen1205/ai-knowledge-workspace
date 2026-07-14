package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

import java.util.UUID;

public interface AssistantSearchPort {
    AssistantSearchPage search(String query, UUID workspaceId, UUID assetId);
}
