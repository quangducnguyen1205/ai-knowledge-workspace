package com.aiknowledgeworkspace.workspacecore.processing.application.artifact;

import java.util.List;
import java.util.UUID;

public interface TranscriptArtifactGateway {

    List<ProcessingTranscriptRow> loadValidatedRows(UUID processingRequestId);
}
