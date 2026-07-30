package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import java.util.List;
import java.util.UUID;

public record ContinueWatchingListView(
        UUID workspaceId,
        int itemCount,
        int maxItems,
        List<ContinueWatchingItem> items
) {
}
