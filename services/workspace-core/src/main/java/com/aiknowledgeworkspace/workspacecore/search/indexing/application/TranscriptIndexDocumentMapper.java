package com.aiknowledgeworkspace.workspacecore.search.indexing.application;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexDocument;
import org.springframework.stereotype.Component;

@Component
public class TranscriptIndexDocumentMapper {

    public TranscriptIndexDocument toDocument(IndexingAssetSource asset, IndexingTranscriptRow transcriptRow) {
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
