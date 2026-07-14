package com.aiknowledgeworkspace.workspacecore.processing.application;

import java.util.UUID;

public record ProcessingJobUpdateCommand(UUID jobId, ProcessingJobStatus status, String rawUpstreamTaskState) {
}
