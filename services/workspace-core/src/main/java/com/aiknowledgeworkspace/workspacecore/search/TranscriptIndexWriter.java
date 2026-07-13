package com.aiknowledgeworkspace.workspacecore.search;

import java.util.List;
import java.util.UUID;

interface TranscriptIndexWriter {
    void ensureTranscriptIndexExists();

    void deleteTranscriptRowsForAsset(UUID assetId);

    void indexTranscriptRows(List<TranscriptIndexWriteOperation> operations);

    void refreshTranscriptIndex();
}
