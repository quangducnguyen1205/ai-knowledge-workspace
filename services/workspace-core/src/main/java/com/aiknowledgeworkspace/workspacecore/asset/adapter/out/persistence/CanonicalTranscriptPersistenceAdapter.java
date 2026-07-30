package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.CanonicalTranscriptContextReadException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
class CanonicalTranscriptPersistenceAdapter implements CanonicalTranscriptStore {

    private final CanonicalTranscriptJpaRepository transcriptRepository;
    private final CanonicalTranscriptContextJdbcRepository contextRepository;

    CanonicalTranscriptPersistenceAdapter(
            CanonicalTranscriptJpaRepository transcriptRepository,
            CanonicalTranscriptContextJdbcRepository contextRepository
    ) {
        this.transcriptRepository = transcriptRepository;
        this.contextRepository = contextRepository;
    }

    @Override
    public List<AssetTranscriptRowView> load(UUID assetId) {
        return sorted(transcriptRepository.findByAssetId(assetId));
    }

    @Override
    public List<AssetTranscriptRowView> loadCanonicalRows(UUID assetId, List<String> transcriptRowIds) {
        if (assetId == null || transcriptRowIds == null || transcriptRowIds.isEmpty()) {
            return List.of();
        }

        Set<String> storedRowIds = new LinkedHashSet<>();
        Set<Integer> segmentConventionIndexes = new LinkedHashSet<>();
        for (String transcriptRowId : transcriptRowIds) {
            if (transcriptRowId == null || transcriptRowId.isBlank()) {
                continue;
            }
            storedRowIds.add(transcriptRowId);
            parseSegmentConvention(transcriptRowId).ifPresent(segmentConventionIndexes::add);
        }

        List<AssetTranscriptRowSnapshot> snapshots = new ArrayList<>();
        if (!storedRowIds.isEmpty()) {
            snapshots.addAll(transcriptRepository.findByAssetIdAndTranscriptRowIdIn(assetId, storedRowIds));
        }
        if (!segmentConventionIndexes.isEmpty()) {
            transcriptRepository.findByAssetIdAndSegmentIndexIn(assetId, segmentConventionIndexes).stream()
                    .filter(snapshot -> snapshot.getTranscriptRowId() == null
                            || snapshot.getTranscriptRowId().isBlank())
                    .forEach(snapshots::add);
        }
        return sorted(snapshots);
    }

    private Optional<Integer> parseSegmentConvention(String transcriptRowId) {
        if (!transcriptRowId.startsWith("segment-")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(transcriptRowId.substring("segment-".length())));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<CanonicalTranscriptContextWindow> loadContextWindows(
            UUID assetId,
            List<CanonicalTranscriptContextTarget> targets
    ) {
        try {
            return contextRepository.load(assetId, targets);
        } catch (DataAccessException | IllegalStateException exception) {
            throw new CanonicalTranscriptContextReadException();
        }
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
