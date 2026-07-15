package com.aiknowledgeworkspace.workspacecore.search.application.port.out;

import java.util.List;

public interface TranscriptSearchQueryPort {

    List<TranscriptSearchHit> search(TranscriptSearchQuery query);
}
