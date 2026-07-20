package com.aiknowledgeworkspace.workspacecore.search.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SearchIndexJobPersistenceAdapter implements SearchIndexJobStore {

    private final AssetSearchIndexJobJpaRepository repository;

    SearchIndexJobPersistenceAdapter(AssetSearchIndexJobJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AssetSearchIndexJob> findById(UUID jobId) {
        return repository.findById(jobId);
    }

    @Override
    public List<AssetSearchIndexJob> findByAssetAndStatuses(
            UUID assetId,
            Collection<AssetSearchIndexJobStatus> statuses
    ) {
        return repository.findByAssetIdAndStatusIn(assetId, statuses);
    }

    @Override
    public List<AssetSearchIndexJob> findByAssetFingerprintAndStatuses(
            UUID assetId,
            String fingerprint,
            Collection<AssetSearchIndexJobStatus> statuses
    ) {
        return repository.findByAssetIdAndSnapshotFingerprintAndStatusIn(assetId, fingerprint, statuses);
    }

    @Override
    public Optional<AssetSearchIndexJob> findLatestIndexed(UUID assetId, String fingerprint) {
        return repository.findFirstByAssetIdAndSnapshotFingerprintAndStatusOrderByIndexedAtDesc(
                assetId,
                fingerprint,
                AssetSearchIndexJobStatus.INDEXED
        );
    }

    @Override
    public Optional<AssetSearchIndexJob> findByRequestOutboxEventId(UUID eventId) {
        return repository.findByRequestOutboxEventId(eventId);
    }

    @Override
    public AssetSearchIndexJob save(AssetSearchIndexJob job) {
        return repository.save(job);
    }

    @Override
    public void deleteByAssetId(UUID assetId) {
        repository.deleteByAssetId(assetId);
    }
}
