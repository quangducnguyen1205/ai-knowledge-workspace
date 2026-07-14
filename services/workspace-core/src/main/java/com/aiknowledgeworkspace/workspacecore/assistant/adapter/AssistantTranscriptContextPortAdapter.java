package com.aiknowledgeworkspace.workspacecore.assistant.adapter;

import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContextPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptSegment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AssistantTranscriptContextPortAdapter implements AssistantTranscriptContextPort {
    private final AssetTranscriptQueryService transcriptQueryService;

    AssistantTranscriptContextPortAdapter(AssetTranscriptQueryService transcriptQueryService) {
        this.transcriptQueryService = transcriptQueryService;
    }

    @Override
    public Optional<AssistantTranscriptContext> findSearchableTranscriptContext(
            UUID assetId, UUID workspaceId, String transcriptRowId, int window
    ) {
        return transcriptQueryService.findSearchableTranscriptContext(assetId, workspaceId, transcriptRowId, window)
                .map(context -> new AssistantTranscriptContext(
                        context.assetId(), context.assetTitle(), context.transcriptRowId(), context.hitSegmentIndex(),
                        context.window(), context.rows().stream().map(row -> new AssistantTranscriptSegment(
                                row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                        )).toList()
                ));
    }
}
