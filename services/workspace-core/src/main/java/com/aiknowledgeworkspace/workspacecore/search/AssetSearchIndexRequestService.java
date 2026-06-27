package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetSearchIndexRequestService {

    private static final List<AssetSearchIndexJobStatus> ACTIVE_STATUSES = List.of(
            AssetSearchIndexJobStatus.PENDING,
            AssetSearchIndexJobStatus.INDEXING
    );

    private final SearchIndexingProperties searchIndexingProperties;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final OutboxEventRepository outboxEventRepository;

    public AssetSearchIndexRequestService(
            SearchIndexingProperties searchIndexingProperties,
            TranscriptSnapshotFingerprintService fingerprintService,
            AssetSearchIndexJobRepository searchIndexJobRepository,
            OutboxEventFactory outboxEventFactory,
            OutboxEventRepository outboxEventRepository
    ) {
        this.searchIndexingProperties = searchIndexingProperties;
        this.fingerprintService = fingerprintService;
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.outboxEventFactory = outboxEventFactory;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public Optional<AssetSearchIndexJob> requestIndexingIfEnabled(
            UUID assetId,
            List<AssetTranscriptRowView> transcriptRows
    ) {
        if (!searchIndexingProperties.isAutoRequestEnabled()) {
            return Optional.empty();
        }

        String snapshotFingerprint = fingerprintService.fingerprint(transcriptRows);
        return Optional.of(createAutomaticRequest(assetId, snapshotFingerprint));
    }

    @Transactional
    public AssetSearchIndexJob createExplicitJob(UUID assetId, String snapshotFingerprint) {
        Optional<AssetSearchIndexJob> alreadyIndexed = findIndexedJob(assetId, snapshotFingerprint);
        if (alreadyIndexed.isPresent()) {
            return alreadyIndexed.get();
        }

        supersedeActiveJobsForOlderFingerprints(assetId, snapshotFingerprint);
        List<AssetSearchIndexJob> activeSameFingerprint = searchIndexJobRepository
                .findByAssetIdAndSnapshotFingerprintAndStatusIn(assetId, snapshotFingerprint, ACTIVE_STATUSES);
        if (!activeSameFingerprint.isEmpty()) {
            return activeSameFingerprint.get(0);
        }

        return searchIndexJobRepository.save(new AssetSearchIndexJob(assetId, snapshotFingerprint));
    }

    private AssetSearchIndexJob createAutomaticRequest(UUID assetId, String snapshotFingerprint) {
        Optional<AssetSearchIndexJob> alreadyIndexed = findIndexedJob(assetId, snapshotFingerprint);
        if (alreadyIndexed.isPresent()) {
            return alreadyIndexed.get();
        }

        supersedeActiveJobsForOlderFingerprints(assetId, snapshotFingerprint);

        List<AssetSearchIndexJob> activeSameFingerprint = searchIndexJobRepository
                .findByAssetIdAndSnapshotFingerprintAndStatusIn(assetId, snapshotFingerprint, ACTIVE_STATUSES);
        if (!activeSameFingerprint.isEmpty()) {
            return activeSameFingerprint.get(0);
        }

        AssetSearchIndexJob searchIndexJob = new AssetSearchIndexJob(assetId, snapshotFingerprint);
        OutboxEvent outboxEvent = outboxEventFactory.assetIndexingRequested(
                assetId,
                searchIndexJob.getId(),
                snapshotFingerprint
        );
        searchIndexJob.attachRequestOutboxEvent(outboxEvent.getId());

        searchIndexJob = searchIndexJobRepository.save(searchIndexJob);
        outboxEventRepository.save(outboxEvent);
        return searchIndexJob;
    }

    private void supersedeActiveJobsForOlderFingerprints(UUID assetId, String currentSnapshotFingerprint) {
        List<AssetSearchIndexJob> activeJobs = searchIndexJobRepository.findByAssetIdAndStatusIn(
                assetId,
                ACTIVE_STATUSES
        );
        for (AssetSearchIndexJob activeJob : activeJobs) {
            if (!currentSnapshotFingerprint.equals(activeJob.getSnapshotFingerprint())) {
                activeJob.markSuperseded();
                searchIndexJobRepository.save(activeJob);
            }
        }
    }

    private Optional<AssetSearchIndexJob> findIndexedJob(UUID assetId, String snapshotFingerprint) {
        return searchIndexJobRepository.findFirstByAssetIdAndSnapshotFingerprintAndStatusOrderByIndexedAtDesc(
                assetId,
                snapshotFingerprint,
                AssetSearchIndexJobStatus.INDEXED
        );
    }
}
