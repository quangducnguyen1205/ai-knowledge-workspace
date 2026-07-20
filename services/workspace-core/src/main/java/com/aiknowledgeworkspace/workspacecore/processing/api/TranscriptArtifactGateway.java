package com.aiknowledgeworkspace.workspacecore.processing.api;

import java.util.List;
import java.util.UUID;

public interface TranscriptArtifactGateway {

    List<ProcessingTranscriptRow> loadRows(UUID processingRequestId);
}
