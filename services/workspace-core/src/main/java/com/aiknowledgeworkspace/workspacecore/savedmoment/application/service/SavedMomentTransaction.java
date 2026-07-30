package com.aiknowledgeworkspace.workspacecore.savedmoment.application.service;

import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write and read boundaries for saved moments. The insert runs in its own transaction so a unique
 * constraint rejection rolls back only that attempt and the caller can still read the winning row.
 */
@Service
class SavedMomentTransaction {

    private final SavedMomentStore savedMomentStore;

    SavedMomentTransaction(SavedMomentStore savedMomentStore) {
        this.savedMomentStore = savedMomentStore;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SavedMomentRecord insert(SavedMomentRecord record) {
        return savedMomentStore.insert(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<SavedMomentRecord> find(String userId, UUID assetId, String transcriptRowId) {
        return savedMomentStore.find(userId, assetId, transcriptRowId);
    }

    @Transactional(readOnly = true)
    Optional<SavedMomentRecord> findOwned(UUID savedMomentId, String userId) {
        return savedMomentStore.findOwned(savedMomentId, userId);
    }

    @Transactional(readOnly = true)
    List<SavedMomentRecord> findRecent(String userId, UUID workspaceId, int limit) {
        return savedMomentStore.findRecent(userId, workspaceId, limit);
    }

    @Transactional
    void delete(UUID savedMomentId) {
        savedMomentStore.delete(savedMomentId);
    }

    @Transactional
    void deleteForAsset(UUID assetId) {
        savedMomentStore.deleteForAsset(assetId);
    }
}
