package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.processing;

import java.util.List;

interface FastApiProcessingClient {

    List<FastApiTranscriptRowResponse> getTranscriptArtifactRows(String processingRequestId);
}
