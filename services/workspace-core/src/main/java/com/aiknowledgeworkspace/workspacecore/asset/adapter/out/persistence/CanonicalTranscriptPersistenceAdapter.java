package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CanonicalTranscriptPersistenceAdapter implements CanonicalTranscriptStore {

    private final CanonicalTranscriptJpaRepository transcriptRepository;

    CanonicalTranscriptPersistenceAdapter(CanonicalTranscriptJpaRepository transcriptRepository) {
        this.transcriptRepository = transcriptRepository;
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
                        row.startMs(),
                        row.endMs(),
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
                row.getStartMs(),
                row.getEndMs(),
                row.getText(),
                row.getCreatedAt()
        );
    }
}
