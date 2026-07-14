package com.aiknowledgeworkspace.workspacecore.search.indexing.application;

import com.aiknowledgeworkspace.workspacecore.search.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.persistence.AssetSearchIndexJobRepository;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingRequestRow;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventCodec;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetSearchIndexRequestService implements IndexingRequestApplication {

    private static final List<AssetSearchIndexJobStatus> ACTIVE_STATUSES = List.of(
            AssetSearchIndexJobStatus.PENDING,
            AssetSearchIndexJobStatus.INDEXING
    );

    private final SearchIndexingProperties searchIndexingProperties;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final IndexingRequestedEventCodec indexingRequestedEventCodec;
    private final OutboxWriter outboxWriter;

    public AssetSearchIndexRequestService(
            SearchIndexingProperties searchIndexingProperties,
            TranscriptSnapshotFingerprintService fingerprintService,
            AssetSearchIndexJobRepository searchIndexJobRepository,
            IndexingRequestedEventCodec indexingRequestedEventCodec,
            OutboxWriter outboxWriter
    ) {
        this.searchIndexingProperties = searchIndexingProperties;
        this.fingerprintService = fingerprintService;
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.indexingRequestedEventCodec = indexingRequestedEventCodec;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void requestIndexingIfEnabled(
            UUID assetId,
            List<IndexingRequestRow> transcriptRows
    ) {
        if (!searchIndexingProperties.isAutoRequestEnabled()) {
            return;
        }

        String snapshotFingerprint = fingerprintService.fingerprint(transcriptRows);
        createAutomaticRequest(assetId, snapshotFingerprint);
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
        OutboxDraft outboxEvent = indexingRequestedEventCodec.encode(new IndexingRequestedEventData(
                assetId,
                searchIndexJob.getId(),
                snapshotFingerprint
        ));
        searchIndexJob.attachRequestOutboxEvent(outboxEvent.eventId());

        searchIndexJob = searchIndexJobRepository.save(searchIndexJob);
        outboxWriter.enqueue(outboxEvent);
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
