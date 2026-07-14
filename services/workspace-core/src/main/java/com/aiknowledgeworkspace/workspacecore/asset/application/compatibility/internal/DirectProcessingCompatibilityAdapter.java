package com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.TranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptSnapshotService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshot;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingCompatibilityGateway;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTaskState;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DirectProcessingCompatibilityAdapter {

    private final DirectProcessingCompatibilityGateway compatibilityGateway;
    private final AssetTranscriptQueryService transcriptQueryService;
    private final AssetTranscriptSnapshotService transcriptSnapshotService;

    public DirectProcessingCompatibilityAdapter(
            DirectProcessingCompatibilityGateway compatibilityGateway,
            AssetTranscriptQueryService transcriptQueryService,
            AssetTranscriptSnapshotService transcriptSnapshotService
    ) {
        this.compatibilityGateway = compatibilityGateway;
        this.transcriptQueryService = transcriptQueryService;
        this.transcriptSnapshotService = transcriptSnapshotService;
    }

    public DirectProcessingUploadResult upload(Resource resource, String originalFilename, String title) {
        return compatibilityGateway.upload(new DirectProcessingUploadCommand(resource, originalFilename, title));
    }

    public DirectProcessingTaskState taskState(String taskId) {
        return compatibilityGateway.taskState(taskId);
    }

    public List<AssetTranscriptRowSnapshot> loadOrCaptureTranscript(Asset asset, ProcessingJobView processingJob) {
        if (processingJob.status() != ProcessingJobStatus.SUCCEEDED) {
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_READY",
                    "Transcript is not ready until processing reaches terminal success"
            );
        }
        return loadOrCaptureTranscript(asset, processingJob.fastapiVideoId());
    }

    @Transactional
    public AssetIndexingSource loadAuthorizedIndexingSourceForCompletedProcessing(UUID assetId, String videoId) {
        Asset asset = transcriptQueryService.loadAuthorizedAsset(assetId);
        List<AssetTranscriptRowSnapshot> rows = loadOrCaptureTranscript(asset, videoId);
        return transcriptQueryService.toIndexingSource(asset, rows);
    }

    private List<AssetTranscriptRowSnapshot> loadOrCaptureTranscript(Asset asset, String videoId) {
        List<AssetTranscriptRowSnapshot> transcriptRows = transcriptQueryService.loadUsableSnapshot(asset.getId());
        if (transcriptRows.isEmpty()) {
            transcriptRows = captureTranscript(asset, videoId);
        }
        transcriptSnapshotService.markTranscriptReady(asset);
        return transcriptRows;
    }

    private List<AssetTranscriptRowSnapshot> captureTranscript(Asset asset, String videoId) {
        List<AssetTranscriptRowInput> rows = compatibilityGateway.transcriptRows(videoId).stream()
                .map(row -> row == null ? null : toInput(row))
                .toList();
        try {
            return transcriptSnapshotService.replaceCanonicalSnapshot(asset, rows);
        } catch (TranscriptUnavailableException exception) {
            transcriptSnapshotService.markProcessingFailed(asset.getId());
            throw exception;
        }
    }

    private AssetTranscriptRowInput toInput(DirectProcessingTranscriptRow row) {
        return new AssetTranscriptRowInput(
                row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
        );
    }
}
