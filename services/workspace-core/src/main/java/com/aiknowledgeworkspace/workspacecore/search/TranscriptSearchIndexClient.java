package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TranscriptSearchIndexClient implements TranscriptIndexWriter {

    private static final int DEFAULT_RESULT_SIZE = 20;
    private static final float TEXT_PHRASE_BOOST = 6.0f;
    private static final float ASSET_TITLE_PHRASE_BOOST = 4.0f;
    private static final MediaType BULK_MEDIA_TYPE = MediaType.parseMediaType("application/x-ndjson");

    private final RestClient elasticsearchRestClient;
    private final ElasticsearchProperties elasticsearchProperties;
    private final ObjectMapper objectMapper;

    public TranscriptSearchIndexClient(
            @Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient,
            ElasticsearchProperties elasticsearchProperties,
            ObjectMapper objectMapper
    ) {
        this.elasticsearchRestClient = elasticsearchRestClient;
        this.elasticsearchProperties = elasticsearchProperties;
        this.objectMapper = objectMapper;
    }

    public JsonNode searchTranscriptRows(String query, UUID workspaceId, UUID assetId, List<UUID> eligibleAssetIds) {
        return execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_search", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildSearchBody(query, workspaceId, assetId, eligibleAssetIds))
                        .retrieve()
                        .body(JsonNode.class),
                "search transcript rows"
        );
    }

    @Override
    public void ensureTranscriptIndexExists() {
        String indexName = elasticsearchProperties.getTranscriptIndexName();
        if (transcriptIndexExists(indexName)) {
            return;
        }

        createTranscriptIndex(indexName);
    }

    @Override
    public void indexTranscriptRows(List<TranscriptIndexWriteOperation> operations) {
        JsonNode bulkResponse = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_bulk", elasticsearchProperties.getTranscriptIndexName())
                        .contentType(BULK_MEDIA_TYPE)
                        .body(buildBulkRequestBody(operations))
                        .retrieve()
                        .body(JsonNode.class),
                "bulk index transcript rows"
        );
        validateBulkResponse(bulkResponse);
    }

    @Override
    public void refreshTranscriptIndex() {
        execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_refresh", elasticsearchProperties.getTranscriptIndexName())
                        .retrieve()
                        .toBodilessEntity(),
                "refresh transcript index"
        );
    }

    @Override
    public void deleteTranscriptRowsForAsset(UUID assetId) {
        JsonNode responseBody = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_delete_by_query?refresh=true",
                                elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "query", Map.of(
                                        "term", Map.of("assetId.keyword", assetId.toString())
                                )
                        ))
                        .retrieve()
                        .body(JsonNode.class),
                "delete transcript documents for asset " + assetId
        );

        validateDeleteResponse(assetId, responseBody);
    }

    public void updateAssetTitle(UUID assetId, String assetTitle) {
        JsonNode responseBody = execute(
                () -> elasticsearchRestClient.post()
                        .uri("/{indexName}/_update_by_query?refresh=true",
                                elasticsearchProperties.getTranscriptIndexName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "script", Map.of(
                                        "source", "ctx._source.assetTitle = params.assetTitle",
                                        "lang", "painless",
                                        "params", Map.of("assetTitle", assetTitle)
                                ),
                                "query", Map.of(
                                        "term", Map.of("assetId.keyword", assetId.toString())
                                )
                        ))
                        .retrieve()
                        .body(JsonNode.class),
                "sync search metadata for asset " + assetId
        );

        validateTitleSyncResponse(assetId, responseBody);
    }

    private boolean transcriptIndexExists(String indexName) {
        try {
            elasticsearchRestClient.head()
                    .uri("/{indexName}", indexName)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (ResourceAccessException exception) {
            throw new ElasticsearchConnectivityException(
                    "Elasticsearch is unavailable while checking transcript index existence",
                    exception
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
            }
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch returned HTTP " + exception.getStatusCode().value()
                            + " while checking transcript index existence",
                    exception
            );
        } catch (RestClientException exception) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch request failed while checking transcript index existence",
                    exception
            );
        }
    }

    private void createTranscriptIndex(String indexName) {
        try {
            elasticsearchRestClient.put()
                    .uri("/{indexName}", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildTranscriptIndexDefinition())
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new ElasticsearchConnectivityException(
                    "Elasticsearch is unavailable while creating transcript index",
                    exception
            );
        } catch (RestClientResponseException exception) {
            if (isIndexAlreadyExistsRace(exception) && transcriptIndexExists(indexName)) {
                return;
            }
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch returned HTTP " + exception.getStatusCode().value()
                            + " while creating transcript index",
                    exception
            );
        } catch (RestClientException exception) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch request failed while creating transcript index",
                    exception
            );
        }
    }

    private boolean isIndexAlreadyExistsRace(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 400
                && exception.getResponseBodyAsString().contains("resource_already_exists_exception");
    }

    private Map<String, Object> buildTranscriptIndexDefinition() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("assetId", textWithKeywordField());
        properties.put("workspaceId", textWithKeywordField());
        properties.put("assetTitle", textWithKeywordField());
        properties.put("transcriptRowId", textWithKeywordField());
        properties.put("segmentIndex", Map.of("type", "integer"));
        properties.put("text", Map.of("type", "text"));
        properties.put("createdAt", Map.of("type", "keyword"));
        properties.put("assetStatus", textWithKeywordField());

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("settings", settings);
        definition.put("mappings", Map.of("properties", properties));
        return definition;
    }

    private Map<String, Object> textWithKeywordField() {
        Map<String, Object> keywordField = new LinkedHashMap<>();
        keywordField.put("type", "keyword");
        keywordField.put("ignore_above", 256);

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "text");
        field.put("fields", Map.of("keyword", keywordField));
        return field;
    }

    private Map<String, Object> buildSearchBody(
            String query,
            UUID workspaceId,
            UUID assetId,
            List<UUID> eligibleAssetIds
    ) {
        List<Map<String, Object>> filterClauses = new ArrayList<>();
        filterClauses.add(termFilter("assetStatus.keyword", "SEARCHABLE"));
        filterClauses.add(termFilter("workspaceId.keyword", workspaceId.toString()));

        if (assetId != null) {
            filterClauses.add(termFilter("assetId.keyword", assetId.toString()));
        } else {
            filterClauses.add(termsFilter(
                    "assetId.keyword",
                    eligibleAssetIds.stream().map(UUID::toString).toList()
            ));
        }

        Map<String, Object> multiMatchQuery = new LinkedHashMap<>();
        multiMatchQuery.put("query", query);
        multiMatchQuery.put("fields", List.of("text^3", "assetTitle"));

        Map<String, Object> boolQuery = new LinkedHashMap<>();
        boolQuery.put("must", List.of(Map.of("multi_match", multiMatchQuery)));
        boolQuery.put("should", buildPhraseBoostClauses(query));
        boolQuery.put("filter", filterClauses);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", DEFAULT_RESULT_SIZE);
        body.put("query", Map.of("bool", boolQuery));
        body.put("sort", buildSortClauses());
        return body;
    }

    private List<Map<String, Object>> buildPhraseBoostClauses(String query) {
        return List.of(
                matchPhraseClause("text", query, TEXT_PHRASE_BOOST),
                matchPhraseClause("assetTitle", query, ASSET_TITLE_PHRASE_BOOST)
        );
    }

    private Map<String, Object> matchPhraseClause(String field, String query, float boost) {
        Map<String, Object> phraseOptions = new LinkedHashMap<>();
        phraseOptions.put("query", query);
        phraseOptions.put("boost", boost);
        return Map.of("match_phrase", Map.of(field, phraseOptions));
    }

    private List<Map<String, Object>> buildSortClauses() {
        return List.of(
                sortClause("_score", "desc"),
                sortClause("segmentIndex", "asc"),
                sortClause("assetId.keyword", "asc"),
                sortClause("transcriptRowId.keyword", "asc", "_last")
        );
    }

    private Map<String, Object> termFilter(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    private Map<String, Object> termsFilter(String field, List<String> values) {
        return Map.of("terms", Map.of(field, values));
    }

    private Map<String, Object> sortClause(String field, String order) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("order", order);
        return Map.of(field, options);
    }

    private Map<String, Object> sortClause(String field, String order, String missingValue) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("order", order);
        options.put("missing", missingValue);
        return Map.of(field, options);
    }

    private String buildBulkRequestBody(List<TranscriptIndexWriteOperation> operations) {
        StringBuilder body = new StringBuilder();

        try {
            for (TranscriptIndexWriteOperation operation : operations) {
                body.append(objectMapper.writeValueAsString(Map.of("index", Map.of("_id", operation.documentId()))))
                        .append('\n');
                body.append(objectMapper.writeValueAsString(operation.document()))
                        .append('\n');
            }
        } catch (JsonProcessingException exception) {
            throw new ElasticsearchIntegrationException("Failed to serialize Elasticsearch bulk request", exception);
        }

        return body.toString();
    }

    private void validateBulkResponse(JsonNode bulkResponse) {
        if (bulkResponse == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch bulk indexing response body was empty");
        }

        JsonNode itemsNode = bulkResponse.path("items");
        if (!itemsNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch bulk indexing response did not include items");
        }

        for (JsonNode itemNode : itemsNode) {
            JsonNode indexNode = itemNode.path("index");
            if (!indexNode.isObject()) {
                throw new ElasticsearchIntegrationException(
                        "Elasticsearch bulk indexing response item did not include index metadata"
                );
            }

            JsonNode statusNode = indexNode.path("status");
            int status = statusNode.canConvertToInt() ? statusNode.asInt() : -1;
            if (status < 200 || status >= 300 || indexNode.hasNonNull("error")) {
                String documentId = indexNode.path("_id").asText("unknown");
                String errorReason = indexNode.path("error").path("reason").asText(null);
                String message = "Elasticsearch bulk indexing failed for document " + documentId
                        + " with status " + status;
                if (StringUtils.hasText(errorReason)) {
                    message = message + ": " + errorReason;
                }
                throw new ElasticsearchIntegrationException(message);
            }
        }
    }

    private void validateDeleteResponse(UUID assetId, JsonNode responseBody) {
        if (responseBody == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch delete response body was empty");
        }

        JsonNode failuresNode = responseBody.path("failures");
        if (!failuresNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch delete response did not include failures");
        }
        if (!failuresNode.isEmpty()) {
            JsonNode firstFailure = failuresNode.get(0);
            String reason = firstFailure.path("cause").path("reason").asText(null);
            String message = "Elasticsearch delete failed for asset " + assetId;
            if (StringUtils.hasText(reason)) {
                message = message + ": " + reason;
            }
            throw new ElasticsearchIntegrationException(message);
        }

        long total = readLong(responseBody, "total", "delete response");
        long deleted = readLong(responseBody, "deleted", "delete response");
        long versionConflicts = readLong(responseBody, "version_conflicts", "delete response");

        if (versionConflicts > 0) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch delete hit " + versionConflicts + " version conflicts for asset " + assetId
            );
        }
        if (deleted != total) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch delete removed only " + deleted + " of " + total
                            + " transcript documents for asset " + assetId
            );
        }
    }

    private void validateTitleSyncResponse(UUID assetId, JsonNode responseBody) {
        if (responseBody == null) {
            throw new ElasticsearchIntegrationException("Elasticsearch title sync response body was empty");
        }

        JsonNode failuresNode = responseBody.path("failures");
        if (!failuresNode.isArray()) {
            throw new ElasticsearchIntegrationException("Elasticsearch title sync response did not include failures");
        }
        if (!failuresNode.isEmpty()) {
            JsonNode firstFailure = failuresNode.get(0);
            String reason = firstFailure.path("cause").path("reason").asText(null);
            String message = "Elasticsearch title sync failed for asset " + assetId;
            if (StringUtils.hasText(reason)) {
                message = message + ": " + reason;
            }
            throw new ElasticsearchIntegrationException(message);
        }

        long total = readLong(responseBody, "total", "title sync response");
        long updated = readLong(responseBody, "updated", "title sync response");
        long noops = readLong(responseBody, "noops", "title sync response");
        long versionConflicts = readLong(responseBody, "version_conflicts", "title sync response");

        if (total <= 0) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch title sync did not match any transcript documents for asset " + assetId
            );
        }
        if (versionConflicts > 0) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch title sync hit " + versionConflicts + " version conflicts for asset " + assetId
            );
        }
        if (updated + noops != total) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch title sync accounted for only " + (updated + noops) + " of " + total
                            + " transcript documents for asset " + assetId
            );
        }
    }

    private long readLong(JsonNode responseBody, String fieldName, String description) {
        JsonNode fieldNode = responseBody.path(fieldName);
        if (!fieldNode.canConvertToLong()) {
            throw new ElasticsearchIntegrationException(
                    "Elasticsearch " + description + " did not include a numeric " + fieldName
            );
        }
        return fieldNode.asLong();
    }

    private <T> T execute(ElasticsearchOperation<T> operation, String description) {
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
    private interface ElasticsearchOperation<T> {
        T run();
    }
}
