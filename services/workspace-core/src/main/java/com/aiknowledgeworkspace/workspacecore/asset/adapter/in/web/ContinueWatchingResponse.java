package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingListView;
import java.util.List;
import java.util.UUID;

public record ContinueWatchingResponse(
        UUID workspaceIdFilter,
        int itemCount,
        int maxItems,
        List<ContinueWatchingItemResponse> items
) {

    static ContinueWatchingResponse from(ContinueWatchingListView view) {
        return new ContinueWatchingResponse(
                view.workspaceId(),
                view.itemCount(),
                view.maxItems(),
                view.items().stream().map(ContinueWatchingItemResponse::from).toList()
        );
    }
}
