package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in;

import java.util.UUID;

/**
 * Product-truth cleanup contract used by Asset deletion. The database foreign key also cascades;
 * this keeps the removal inside the same deletion transaction rather than relying on the cascade
 * alone.
 */
public interface SavedMomentAssetCleanupUseCase {

    void deleteForAsset(UUID assetId);
}
