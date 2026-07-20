package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.messaging.IndexingRequestEventFactory;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.search.api.IndexingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.search.api.IndexingRequestRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetSearchIndexRequestService implements IndexingRequestUseCase {

    private static final List<AssetSearchIndexJobStatus> ACTIVE_STATUSES = List.of(
            AssetSearchIndexJobStatus.PENDING,
            AssetSearchIndexJobStatus.INDEXING
    );

    private final SearchIndexingProperties searchIndexingProperties;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final SearchIndexJobStore searchIndexJobStore;
    private final IndexingRequestEventFactory indexingRequestEventFactory;
    private final OutboxWriter outboxWriter;

    public AssetSearchIndexRequestService(
            SearchIndexingProperties searchIndexingProperties,
            TranscriptSnapshotFingerprintService fingerprintService,
            SearchIndexJobStore searchIndexJobStore,
            IndexingRequestEventFactory indexingRequestEventFactory,
            OutboxWriter outboxWriter
    ) {
        this.searchIndexingProperties = searchIndexingProperties;
        this.fingerprintService = fingerprintService;
        this.searchIndexJobStore = searchIndexJobStore;
        this.indexingRequestEventFactory = indexingRequestEventFactory;
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
        List<AssetSearchIndexJob> activeSameFingerprint = searchIndexJobStore
                .findByAssetFingerprintAndStatuses(assetId, snapshotFingerprint, ACTIVE_STATUSES);
        if (!activeSameFingerprint.isEmpty()) {
            return activeSameFingerprint.get(0);
        }

        return searchIndexJobStore.save(new AssetSearchIndexJob(assetId, snapshotFingerprint));
    }

    private AssetSearchIndexJob createAutomaticRequest(UUID assetId, String snapshotFingerprint) {
        Optional<AssetSearchIndexJob> alreadyIndexed = findIndexedJob(assetId, snapshotFingerprint);
        if (alreadyIndexed.isPresent()) {
            return alreadyIndexed.get();
        }

        supersedeActiveJobsForOlderFingerprints(assetId, snapshotFingerprint);

        List<AssetSearchIndexJob> activeSameFingerprint = searchIndexJobStore
                .findByAssetFingerprintAndStatuses(assetId, snapshotFingerprint, ACTIVE_STATUSES);
        if (!activeSameFingerprint.isEmpty()) {
            return activeSameFingerprint.get(0);
        }

        AssetSearchIndexJob searchIndexJob = new AssetSearchIndexJob(assetId, snapshotFingerprint);
        OutboxDraft outboxEvent = indexingRequestEventFactory.create(
                assetId,
                searchIndexJob.getId(),
                snapshotFingerprint
        );
        searchIndexJob.attachRequestOutboxEvent(outboxEvent.eventId());

        searchIndexJob = searchIndexJobStore.save(searchIndexJob);
        outboxWriter.enqueue(outboxEvent);
        return searchIndexJob;
    }

    private void supersedeActiveJobsForOlderFingerprints(UUID assetId, String currentSnapshotFingerprint) {
        List<AssetSearchIndexJob> activeJobs = searchIndexJobStore.findByAssetAndStatuses(
                assetId,
                ACTIVE_STATUSES
        );
        for (AssetSearchIndexJob activeJob : activeJobs) {
            if (!currentSnapshotFingerprint.equals(activeJob.getSnapshotFingerprint())) {
                activeJob.markSuperseded();
                searchIndexJobStore.save(activeJob);
            }
        }
    }

    private Optional<AssetSearchIndexJob> findIndexedJob(UUID assetId, String snapshotFingerprint) {
        return searchIndexJobStore.findLatestIndexed(assetId, snapshotFingerprint);
    }
}
