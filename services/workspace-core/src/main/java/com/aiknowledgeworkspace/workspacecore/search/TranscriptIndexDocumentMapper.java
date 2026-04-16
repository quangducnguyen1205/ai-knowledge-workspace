package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TranscriptIndexDocumentMapper {

    public TranscriptIndexDocument toDocument(
            Asset asset,
            AssetTranscriptRowSnapshot transcriptRow,
            AssetStatus indexedAssetStatus
    ) {
        return new TranscriptIndexDocument(
                asset.getId(),
                asset.getWorkspaceId(),
                asset.getTitle(),
                transcriptRow.getTranscriptRowId(),
                transcriptRow.getSegmentIndex(),
                transcriptRow.getText(),
                transcriptRow.getCreatedAt(),
                indexedAssetStatus
        );
    }

    public String toDocumentId(Asset asset, AssetTranscriptRowSnapshot transcriptRow) {
        // Keep document IDs stable so a re-index overwrites the same transcript row document.
        String transcriptRowId = StringUtils.hasText(transcriptRow.getTranscriptRowId())
                ? transcriptRow.getTranscriptRowId()
                : "segment-" + transcriptRow.getSegmentIndex();
        return asset.getId() + "-" + transcriptRowId;
    }
}
