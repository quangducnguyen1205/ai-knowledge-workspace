package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

import java.util.UUID;

public class TranscriptRowNotFoundException extends RuntimeException {

    public TranscriptRowNotFoundException(UUID assetId, String transcriptRowId) {
        super("Transcript row not found for asset " + assetId + ": " + transcriptRowId);
    }
}
