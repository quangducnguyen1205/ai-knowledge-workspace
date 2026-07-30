package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

import java.util.UUID;

public record SearchCanonicalContextTarget(
        UUID assetId,
        String transcriptRowId,
        Integer segmentIndex
) {
}
