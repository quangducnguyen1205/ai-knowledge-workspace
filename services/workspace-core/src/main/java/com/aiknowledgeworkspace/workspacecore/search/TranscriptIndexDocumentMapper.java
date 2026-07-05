package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import org.springframework.stereotype.Component;

@Component
public class TranscriptIndexDocumentMapper {

    public TranscriptIndexDocument toDocument(
            AssetIndexingSource asset,
            AssetTranscriptRowView transcriptRow,
            AssetStatus indexedAssetStatus
    ) {
        return new TranscriptIndexDocument(
                asset.assetId(),
                asset.workspaceId(),
                asset.assetTitle(),
                transcriptRow.id(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt(),
                indexedAssetStatus
        );
    }
}
