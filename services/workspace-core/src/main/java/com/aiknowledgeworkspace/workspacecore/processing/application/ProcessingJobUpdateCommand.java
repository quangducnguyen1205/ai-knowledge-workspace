package com.aiknowledgeworkspace.workspacecore.processing.application;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import java.util.UUID;

public record ProcessingJobUpdateCommand(UUID jobId, ProcessingJobStatus status, String rawUpstreamTaskState) {
}
