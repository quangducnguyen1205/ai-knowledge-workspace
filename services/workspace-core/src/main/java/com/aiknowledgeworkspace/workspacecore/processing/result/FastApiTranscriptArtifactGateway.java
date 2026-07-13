package com.aiknowledgeworkspace.workspacecore.processing.result;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingTranscriptRow;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class FastApiTranscriptArtifactGateway implements TranscriptArtifactGateway {

    private final FastApiProcessingClient fastApiProcessingClient;
    private final TranscriptArtifactValidator transcriptArtifactValidator;

    FastApiTranscriptArtifactGateway(
            FastApiProcessingClient fastApiProcessingClient,
            TranscriptArtifactValidator transcriptArtifactValidator
    ) {
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.transcriptArtifactValidator = transcriptArtifactValidator;
    }

    @Override
    public List<ProcessingTranscriptRow> loadValidatedRows(UUID processingRequestId) {
        try {
            return transcriptArtifactValidator.validate(
                    fastApiProcessingClient.getTranscriptArtifactRows(processingRequestId.toString())
            ).stream().map(this::toProcessingRow).toList();
        } catch (FastApiIntegrationException exception) {
            throw new TranscriptArtifactAccessException(exception.getMessage(), exception);
        }
    }

    private ProcessingTranscriptRow toProcessingRow(FastApiTranscriptRowResponse row) {
        return new ProcessingTranscriptRow(
                row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
        );
    }
}
