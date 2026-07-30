package com.aiknowledgeworkspace.workspacecore.savedmoment.application.model;

import java.time.Instant;
import java.util.UUID;

/** Persisted saved-moment identity. Presentation data is always resolved from canonical Asset state. */
public record SavedMomentRecord(
        UUID savedMomentId,
        String userId,
        UUID workspaceId,
        UUID assetId,
        String transcriptRowId,
        Instant savedAt
) {
}
