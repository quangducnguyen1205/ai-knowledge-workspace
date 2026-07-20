package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexDocument;
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
