package com.aiknowledgeworkspace.workspacecore.integration.fastapi.processing.internal;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.ProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactAccessException;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactGateway;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactValidator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TranscriptArtifactGatewayAdapter implements TranscriptArtifactGateway {

    private final FastApiProcessingClient client;
    private final TranscriptArtifactValidator validator;

    TranscriptArtifactGatewayAdapter(FastApiProcessingClient client, TranscriptArtifactValidator validator) {
        this.client = client;
        this.validator = validator;
    }

    @Override
    public List<ProcessingTranscriptRow> loadValidatedRows(UUID processingRequestId) {
        try {
            List<ProcessingTranscriptRow> rows = client.getTranscriptArtifactRows(processingRequestId.toString()).stream()
                    .map(row -> row == null ? null : new ProcessingTranscriptRow(
                            row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                    ))
                    .toList();
            return validator.validate(rows);
        } catch (FastApiIntegrationException exception) {
            throw new TranscriptArtifactAccessException(exception.getMessage(), exception);
        }
    }
}
