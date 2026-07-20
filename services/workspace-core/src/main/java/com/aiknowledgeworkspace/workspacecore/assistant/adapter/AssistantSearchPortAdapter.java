package com.aiknowledgeworkspace.workspacecore.assistant.adapter;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AssistantSearchPortAdapter implements AssistantSearchPort {
    private final SearchQueryUseCase searchQueries;

    AssistantSearchPortAdapter(SearchQueryUseCase searchQueries) { this.searchQueries = searchQueries; }

    @Override
    public AssistantSearchPage search(String query, UUID workspaceId, UUID assetId) {
        SearchResult response = searchQueries.search(new SearchQuery(query, workspaceId, assetId));
        return new AssistantSearchPage(response.workspaceIdFilter(), response.hits().stream()
                .map(result -> new AssistantSearchHit(
                        result.assetId(), result.assetTitle(), result.transcriptRowId(), result.segmentIndex(),
                        result.text(), result.createdAt(), result.score()
                )).toList());
    }
}
