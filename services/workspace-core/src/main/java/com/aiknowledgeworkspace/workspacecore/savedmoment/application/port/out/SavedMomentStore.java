package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out;

import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedMomentStore {

    /**
     * Inserts one saved moment. Throws {@link SavedMomentAlreadySavedException} when the unique
     * constraint already holds, so the caller can return the existing record instead.
     */
    SavedMomentRecord insert(SavedMomentRecord record);

    Optional<SavedMomentRecord> find(String userId, UUID assetId, String transcriptRowId);

    Optional<SavedMomentRecord> findOwned(UUID savedMomentId, String userId);

    List<SavedMomentRecord> findRecent(String userId, UUID workspaceId, int limit);

    void delete(UUID savedMomentId);

    void deleteForAsset(UUID assetId);
}
