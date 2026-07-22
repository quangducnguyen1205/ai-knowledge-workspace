package com.aiknowledgeworkspace.workspacecore.search.adapter.in.module;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.in.SearchQueryUseCase;
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
                        result.startMs(), result.endMs(), result.text(), result.createdAt(), result.score()
                )).toList());
    }
}
