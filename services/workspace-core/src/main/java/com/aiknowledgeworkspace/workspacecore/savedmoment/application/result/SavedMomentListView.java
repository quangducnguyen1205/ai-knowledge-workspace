package com.aiknowledgeworkspace.workspacecore.savedmoment.application.result;

import java.util.List;
import java.util.UUID;

public record SavedMomentListView(
        UUID workspaceId,
        int savedMomentCount,
        int maxItems,
        List<SavedMomentView> items
) {
}
