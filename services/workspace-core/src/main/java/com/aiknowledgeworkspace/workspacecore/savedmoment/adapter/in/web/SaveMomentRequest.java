package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web;

import java.util.UUID;

public record SaveMomentRequest(UUID assetId, String transcriptRowId) {
}
