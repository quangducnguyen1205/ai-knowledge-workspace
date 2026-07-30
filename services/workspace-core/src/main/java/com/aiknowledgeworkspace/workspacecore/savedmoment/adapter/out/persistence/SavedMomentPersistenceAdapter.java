package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentAlreadySavedException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class SavedMomentPersistenceAdapter implements SavedMomentStore {

    private final SavedMomentJpaRepository savedMomentRepository;

    SavedMomentPersistenceAdapter(SavedMomentJpaRepository savedMomentRepository) {
        this.savedMomentRepository = savedMomentRepository;
    }

    @Override
    public SavedMomentRecord insert(SavedMomentRecord record) {
        try {
            return toRecord(savedMomentRepository.saveAndFlush(new SavedMomentEntry(
                    record.savedMomentId(),
                    record.userId(),
                    record.workspaceId(),
                    record.assetId(),
                    record.transcriptRowId(),
                    record.savedAt()
            )));
        } catch (DataIntegrityViolationException exception) {
            throw new SavedMomentAlreadySavedException(exception);
        }
    }

    @Override
    public Optional<SavedMomentRecord> find(String userId, UUID assetId, String transcriptRowId) {
        return savedMomentRepository
                .findByUserIdAndAssetIdAndTranscriptRowId(userId, assetId, transcriptRowId)
                .map(this::toRecord);
    }

    @Override
    public Optional<SavedMomentRecord> findOwned(UUID savedMomentId, String userId) {
        return savedMomentRepository.findByIdAndUserId(savedMomentId, userId).map(this::toRecord);
    }

    @Override
    public List<SavedMomentRecord> findRecent(String userId, UUID workspaceId, int limit) {
        return savedMomentRepository
                .findByUserIdAndWorkspaceIdOrderBySavedAtDescIdDesc(
                        userId, workspaceId, PageRequest.of(0, limit)
                )
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public void delete(UUID savedMomentId) {
        savedMomentRepository.deleteById(savedMomentId);
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        savedMomentRepository.deleteByAssetId(assetId);
    }

    private SavedMomentRecord toRecord(SavedMomentEntry entry) {
        return new SavedMomentRecord(
                entry.getId(),
                entry.getUserId(),
                entry.getWorkspaceId(),
                entry.getAssetId(),
                entry.getTranscriptRowId(),
                entry.getSavedAt()
        );
    }
}
