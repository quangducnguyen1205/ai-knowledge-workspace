package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web;

import java.util.List;
import java.util.UUID;

public record SavedMomentListResponse(
        UUID workspaceIdFilter,
        int savedMomentCount,
        int maxItems,
        List<SavedMomentResponse> items
) {
}
