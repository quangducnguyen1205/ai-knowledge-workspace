package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchService {

    private static final int DEFAULT_RESULT_SIZE = 20;

    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;

    public SearchService(
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient,
            ElasticsearchProperties elasticsearchProperties
    ) {
        this.elasticsearchRestClient = elasticsearchRestClient;
        this.elasticsearchProperties = elasticsearchProperties;
    }

    public SearchResponse search(String query, UUID assetId) {
        String normalizedQuery = normalizeQuery(query);
        JsonNode responseBody = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_search", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildSearchBody(normalizedQuery, assetId))
                        .retrieve()
                        .body(JsonNode.class),
                "search transcript rows"
        );

        return toSearchResponse(normalizedQuery, assetId, responseBody);
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter q is required");
        }
        return query.trim();
    }

    private Map<String, Object> buildSearchBody(String query, UUID assetId) {
        List<Map<String, Object>> filterClauses = new ArrayList<>();
        filterClauses.add(termFilter("assetStatus.keyword", AssetStatus.SEARCHABLE.name()));

        if (assetId != null) {
            filterClauses.add(termFilter("assetId.keyword", assetId.toString()));
        }

        Map<String, Object> multiMatchQuery = new LinkedHashMap<>();
        multiMatchQuery.put("query", query);
        multiMatchQuery.put("fields", List.of("text^3", "assetTitle"));

        Map<String, Object> boolQuery = new LinkedHashMap<>();
        boolQuery.put("must", List.of(Map.of("multi_match", multiMatchQuery)));
        boolQuery.put("filter", filterClauses);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", DEFAULT_RESULT_SIZE);
        body.put("query", Map.of("bool", boolQuery));
        body.put("sort", List.of("_score", Map.of("segmentIndex", Map.of("order", "asc"))));
        return body;
    }

    private Map<String, Object> termFilter(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    private SearchResponse toSearchResponse(String query, UUID assetId, JsonNode responseBody) {
        if (responseBody == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch search response body was empty");
        }

        JsonNode hitsNode = responseBody.path("hits").path("hits");
        if (!hitsNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch search response did not include hits");
        }

        List<SearchResultResponse> results = new ArrayList<>();
        for (JsonNode hitNode : hitsNode) {
            JsonNode sourceNode = hitNode.path("_source");
            if (!sourceNode.isObject()) {
                throw new ElasticsearchIntegrationException("Elasticsearch search hit did not include _source");
            }

            results.add(new SearchResultResponse(
                    parseAssetId(sourceNode),
                    readText(sourceNode, "assetTitle"),
                    readText(sourceNode, "transcriptRowId"),
                    readInteger(sourceNode, "segmentIndex"),
                    readText(sourceNode, "text"),
                    readText(sourceNode, "createdAt"),
                    readScore(hitNode)
            ));
        }

        // TODO: add workspace-scoped filters once workspace ownership is persisted.
        // TODO: consider a richer Elasticsearch query once keyword-only baseline search is proven useful.
        return new SearchResponse(query, assetId, results.size(), results);
    }

    private UUID parseAssetId(JsonNode sourceNode) {
        String assetId = readText(sourceNode, "assetId");
        if (!StringUtils.hasText(assetId)) {
            throw new ElasticsearchIntegrationException("Elasticsearch search hit did not include assetId");
        }

        try {
            return UUID.fromString(assetId);
        } catch (IllegalArgumentException exception) {
            throw new ElasticsearchIntegrationException("Elasticsearch search hit included an invalid assetId", exception);
        }
    }

    private String readText(JsonNode sourceNode, String fieldName) {
        JsonNode fieldNode = sourceNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private Integer readInteger(JsonNode sourceNode, String fieldName) {
        JsonNode fieldNode = sourceNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isInt() || !fieldNode.canConvertToInt()) {
            throw new ElasticsearchIntegrationException("Elasticsearch search hit included a non-numeric value for " + fieldName);
        }
        return fieldNode.asInt();
    }

    private Double readScore(JsonNode hitNode) {
        JsonNode scoreNode = hitNode.path("_score");
        if (scoreNode.isMissingNode() || scoreNode.isNull()) {
            return null;
        }
        return scoreNode.asDouble();
    }

    private <T> T execute(SearchOperation<T> operation, String description) {
        try {
            return operation.run();
        } catch (ResourceAccessException exception) {
            throw new ElasticsearchConnectivityException(
                    "Elasticsearch is unavailable while trying to " + description,
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch returned HTTP " + exception.getStatusCode().value()
                            + " while trying to " + description,
                    exception
            );
        } catch (RestClientException exception) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch request failed while trying to " + description,
                    exception
            );
        }
    }

    @FunctionalInterface
    private interface SearchOperation<T> {
        T run();
    }
}
