package com.aiknowledgeworkspace.workspacecore.search.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.port.in.SearchQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchQueryUseCase searchQueries;

    public SearchController(SearchQueryUseCase searchQueries) {
        this.searchQueries = searchQueries;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(value = "assetId", required = false) UUID assetId
    ) {
        SearchResult result = searchQueries.search(new SearchQuery(query, workspaceId, assetId));
        return new SearchResponse(
                result.query(),
                result.workspaceIdFilter(),
                result.assetIdFilter(),
                result.hits().size(),
                result.hits().stream()
                        .map(hit -> new SearchResultResponse(
                                hit.assetId(),
                                hit.assetTitle(),
                                hit.transcriptRowId(),
                                hit.segmentIndex(),
                                hit.startMs(),
                                hit.endMs(),
                                hit.text(),
                                hit.createdAt(),
                                hit.score()
                        ))
                        .toList()
        );
    }
}
