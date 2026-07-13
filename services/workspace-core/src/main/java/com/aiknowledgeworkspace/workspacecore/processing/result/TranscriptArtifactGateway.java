package com.aiknowledgeworkspace.workspacecore.processing.result;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingTranscriptRow;
import java.util.List;
import java.util.UUID;

interface TranscriptArtifactGateway {
    List<ProcessingTranscriptRow> loadValidatedRows(UUID processingRequestId);
}
