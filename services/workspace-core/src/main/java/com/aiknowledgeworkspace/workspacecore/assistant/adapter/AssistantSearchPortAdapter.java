package com.aiknowledgeworkspace.workspacecore.assistant.adapter;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResponse;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AssistantSearchPortAdapter implements AssistantSearchPort {
    private final SearchService searchService;

    AssistantSearchPortAdapter(SearchService searchService) { this.searchService = searchService; }

    @Override
    public AssistantSearchPage search(String query, UUID workspaceId, UUID assetId) {
        SearchResponse response = searchService.search(query, workspaceId, assetId);
        return new AssistantSearchPage(response.workspaceIdFilter(), response.results().stream()
                .map(result -> new AssistantSearchHit(
                        result.assetId(), result.assetTitle(), result.transcriptRowId(), result.segmentIndex(),
                        result.text(), result.createdAt(), result.score()
                )).toList());
    }
}
