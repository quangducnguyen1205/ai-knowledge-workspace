package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TranscriptIndexDocumentMapper {

    public TranscriptIndexDocument toDocument(
            Asset asset,
            FastApiTranscriptRowResponse transcriptRow,
            AssetStatus indexedAssetStatus
    ) {
        return new TranscriptIndexDocument(
                asset.getId(),
                asset.getTitle(),
                transcriptRow.id(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt(),
                indexedAssetStatus
        );
    }

    public String toDocumentId(Asset asset, FastApiTranscriptRowResponse transcriptRow) {
        String transcriptRowId = StringUtils.hasText(transcriptRow.id())
                ? transcriptRow.id()
                : "segment-" + transcriptRow.segmentIndex();
        return asset.getId() + "-" + transcriptRowId;
    }
}
