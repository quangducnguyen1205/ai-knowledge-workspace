package com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AssetPersistenceAdapter implements AssetStore, CanonicalTranscriptStore {

    private final AssetJpaRepository assetRepository;
    private final CanonicalTranscriptJpaRepository transcriptRepository;

    AssetPersistenceAdapter(
            AssetJpaRepository assetRepository,
            CanonicalTranscriptJpaRepository transcriptRepository
    ) {
        this.assetRepository = assetRepository;
        this.transcriptRepository = transcriptRepository;
    }

    @Override
    public Optional<Asset> findById(UUID assetId) {
        return assetRepository.findById(assetId);
    }

    @Override
    public List<Asset> findByWorkspaceId(UUID workspaceId) {
        return assetRepository.findByWorkspaceIdOrderByCreatedAtDescIdDesc(workspaceId);
    }

    @Override
    public long countByWorkspaceId(UUID workspaceId) {
        return assetRepository.countByWorkspaceId(workspaceId);
    }

    @Override
    public Asset save(Asset asset) {
        return assetRepository.save(asset);
    }

    @Override
    public void delete(Asset asset) {
        assetRepository.delete(asset);
    }

    @Override
    public List<AssetTranscriptRowView> load(UUID assetId) {
        return sorted(transcriptRepository.findByAssetId(assetId));
    }

    @Override
    public List<AssetTranscriptRowView> replace(UUID assetId, List<AssetTranscriptRowInput> rows) {
        transcriptRepository.deleteByAssetId(assetId);
        transcriptRepository.flush();
        List<AssetTranscriptRowSnapshot> snapshots = rows.stream()
                .map(row -> new AssetTranscriptRowSnapshot(
                        assetId,
                        row.id(),
                        row.videoId(),
                        row.segmentIndex(),
                        row.text(),
                        row.createdAt()
                ))
                .toList();
        return sorted(transcriptRepository.saveAll(snapshots));
    }

    @Override
    public void delete(UUID assetId) {
        transcriptRepository.deleteByAssetId(assetId);
    }

    private List<AssetTranscriptRowView> sorted(List<AssetTranscriptRowSnapshot> snapshots) {
        return snapshots.stream()
                .sorted(Comparator.comparing(
                        AssetTranscriptRowSnapshot::getSegmentIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(this::toView)
                .toList();
    }

    private AssetTranscriptRowView toView(AssetTranscriptRowSnapshot row) {
        return new AssetTranscriptRowView(
                row.getTranscriptRowId(),
                row.getVideoId(),
                row.getSegmentIndex(),
                row.getText(),
                row.getCreatedAt()
        );
    }
}
