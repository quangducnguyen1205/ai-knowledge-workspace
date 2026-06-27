package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    public String toDocumentId(AssetIndexingSource asset, AssetTranscriptRowView transcriptRow) {
        // Keep document IDs stable so a re-index overwrites the same transcript row document.
        String transcriptRowId = StringUtils.hasText(transcriptRow.id())
                ? transcriptRow.id()
                : "segment-" + transcriptRow.segmentIndex();
        return asset.assetId() + "-" + transcriptRowId;
    }
}
