package com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.elasticsearch;

import java.util.List;
import java.util.UUID;

public interface TranscriptIndexWriter {
    void ensureTranscriptIndexExists();

    void deleteTranscriptRowsForAsset(UUID assetId);

    void indexTranscriptRows(List<TranscriptIndexWriteOperation> operations);

    void refreshTranscriptIndex();
}
