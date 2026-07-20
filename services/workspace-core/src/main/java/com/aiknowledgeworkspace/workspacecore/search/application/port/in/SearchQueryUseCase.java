package com.aiknowledgeworkspace.workspacecore.search.application.port.in;

import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;

public interface SearchQueryUseCase {

    SearchResult search(SearchQuery query);
}
