package com.aiknowledgeworkspace.workspacecore.integration.fastapi.processing.internal;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingCompatibilityGateway;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingConnectivityException;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTaskState;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiConnectivityException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class DirectProcessingCompatibilityGatewayAdapter implements DirectProcessingCompatibilityGateway {

    private final FastApiProcessingClient client;

    DirectProcessingCompatibilityGatewayAdapter(FastApiProcessingClient client) {
        this.client = client;
    }

    @Override
    public DirectProcessingUploadResult upload(DirectProcessingUploadCommand command) {
        try {
            FastApiUploadResponse response = client.uploadVideo(
                    command.resource(), command.originalFilename(), command.title()
            );
            validateUploadResponse(response);
            ProcessingJobStatus processingStatus = mapTaskStatus(response.status());
            return new DirectProcessingUploadResult(
                    response.taskId(), response.videoId(), response.status(), processingStatus,
                    processingStatus == ProcessingJobStatus.FAILED ? AssetStatus.FAILED : AssetStatus.PROCESSING
            );
        } catch (FastApiConnectivityException exception) {
            throw new DirectProcessingConnectivityException(exception.getMessage(), exception);
        } catch (FastApiIntegrationException exception) {
            throw new com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException(
                    exception.getMessage(), exception
            );
        }
    }

    @Override
    public DirectProcessingTaskState taskState(String taskId) {
        try {
            FastApiTaskStatusResponse response = client.getTaskStatus(taskId);
            validateTaskStatusResponse(response);
            ProcessingJobStatus processingStatus = mapTaskStatus(response.status());
            return new DirectProcessingTaskState(response.status(), processingStatus, mapAssetStatus(processingStatus));
        } catch (FastApiConnectivityException exception) {
            throw new DirectProcessingConnectivityException(exception.getMessage(), exception);
        } catch (FastApiIntegrationException exception) {
            throw new com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException(
                    exception.getMessage(), exception
            );
        }
    }

    @Override
    public List<DirectProcessingTranscriptRow> transcriptRows(String videoId) {
        try {
            return client.getTranscript(videoId).stream()
                    .map(row -> row == null ? null : new DirectProcessingTranscriptRow(
                            row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                    ))
                    .toList();
        } catch (FastApiConnectivityException exception) {
            throw new DirectProcessingConnectivityException(exception.getMessage(), exception);
        } catch (FastApiIntegrationException exception) {
            throw new com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException(
                    exception.getMessage(), exception
            );
        }
    }

    private void validateUploadResponse(FastApiUploadResponse response) {
        if (response == null) {
            throw new FastApiIntegrationException("FastAPI upload response body was empty");
        }
        if (!StringUtils.hasText(response.taskId())) {
            throw new FastApiIntegrationException("FastAPI upload response did not include task_id");
        }
        if (!StringUtils.hasText(response.videoId())) {
            throw new FastApiIntegrationException("FastAPI upload response did not include video_id");
        }
    }

    private void validateTaskStatusResponse(FastApiTaskStatusResponse response) {
        if (response == null) {
            throw new FastApiIntegrationException("FastAPI task status response body was empty");
        }
        if (!StringUtils.hasText(response.status())) {
            throw new FastApiIntegrationException("FastAPI task status response did not include status");
        }
    }

    private ProcessingJobStatus mapTaskStatus(String upstreamStatus) {
        if (!StringUtils.hasText(upstreamStatus)) {
            return ProcessingJobStatus.PENDING;
        }
        return switch (upstreamStatus.trim().toLowerCase(Locale.ROOT)) {
            case "pending", "queued", "created" -> ProcessingJobStatus.PENDING;
            case "running", "processing", "started", "in_progress" -> ProcessingJobStatus.RUNNING;
            case "success", "succeeded", "completed", "complete", "ready" -> ProcessingJobStatus.SUCCEEDED;
            case "failed", "error" -> ProcessingJobStatus.FAILED;
            default -> ProcessingJobStatus.RUNNING;
        };
    }

    private AssetStatus mapAssetStatus(ProcessingJobStatus processingStatus) {
        return processingStatus == ProcessingJobStatus.FAILED ? AssetStatus.FAILED : AssetStatus.PROCESSING;
    }
}
