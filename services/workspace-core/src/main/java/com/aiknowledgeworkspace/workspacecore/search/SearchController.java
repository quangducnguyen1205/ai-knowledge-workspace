package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResponse;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchService;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(value = "assetId", required = false) UUID assetId
    ) {
        return searchService.search(query, workspaceId, assetId);
    }
}
