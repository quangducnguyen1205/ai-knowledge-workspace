package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.processing;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.processing.api.TranscriptArtifactAccessException;
import com.aiknowledgeworkspace.workspacecore.processing.api.TranscriptArtifactGateway;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TranscriptArtifactGatewayAdapter implements TranscriptArtifactGateway {

    private final FastApiProcessingClient client;
    TranscriptArtifactGatewayAdapter(FastApiProcessingClient client) {
        this.client = client;
    }

    @Override
    public List<ProcessingTranscriptRow> loadRows(UUID processingRequestId) {
        try {
            List<ProcessingTranscriptRow> rows = client.getTranscriptArtifactRows(processingRequestId.toString()).stream()
                    .map(row -> row == null ? null : new ProcessingTranscriptRow(
                            row.id(), row.videoId(), row.segmentIndex(), row.startMs(), row.endMs(),
                            row.text(), row.createdAt()
                    ))
                    .toList();
            return rows;
        } catch (FastApiIntegrationException exception) {
            throw new TranscriptArtifactAccessException(exception.getMessage(), exception);
        }
    }
}
