package com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.elasticsearch;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import org.springframework.stereotype.Component;

@Component
public class TranscriptIndexDocumentMapper {

    public TranscriptIndexDocument toDocument(
            IndexingAssetSource asset,
            IndexingTranscriptRow transcriptRow
    ) {
        return new TranscriptIndexDocument(
                asset.assetId(),
                asset.workspaceId(),
                asset.assetTitle(),
                transcriptRow.id(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt(),
                "SEARCHABLE"
        );
    }
}
