package com.aiknowledgeworkspace.workspacecore.integration.fastapi.processing.internal;

import java.util.List;

interface FastApiProcessingClient {

    List<FastApiTranscriptRowResponse> getTranscriptArtifactRows(String processingRequestId);
}
