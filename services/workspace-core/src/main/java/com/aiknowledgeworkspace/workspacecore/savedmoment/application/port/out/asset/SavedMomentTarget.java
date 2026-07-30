package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset;

import java.util.UUID;

public record SavedMomentTarget(UUID assetId, String transcriptRowId) {
}
