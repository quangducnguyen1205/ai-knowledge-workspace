package com.aiknowledgeworkspace.workspacecore.savedmoment.application.command;

import java.util.UUID;

public record SaveMomentCommand(UUID assetId, String transcriptRowId) {
}
