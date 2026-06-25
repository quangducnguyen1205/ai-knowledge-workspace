package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptContextResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.asset.TranscriptRowNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.TranscriptUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.SearchResponse;
import com.aiknowledgeworkspace.workspacecore.search.SearchResultResponse;
import com.aiknowledgeworkspace.workspacecore.search.SearchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssistantContextService {

    static final int DEFAULT_MAX_SOURCES = 5;
    static final int MAX_SOURCES = 10;
    static final int DEFAULT_CONTEXT_WINDOW = 1;
    static final int MAX_CONTEXT_WINDOW = 5;
    static final int MAX_QUERY_LENGTH = 500;
    static final int MAX_SOURCE_TEXT_LENGTH = 2_000;

    private final SearchService searchService;
    private final AssetService assetService;

    public AssistantContextService(SearchService searchService, AssetService assetService) {
        this.searchService = searchService;
        this.assetService = assetService;
    }

    public AssistantContextResponse buildContext(AssistantContextRequest request) {
        NormalizedRequest normalizedRequest = normalize(request);
        SearchResponse searchResponse = searchService.search(
                normalizedRequest.query(),
                normalizedRequest.workspaceId(),
                normalizedRequest.assetId()
        );

        List<AssistantContextSourceResponse> sources = new ArrayList<>();
        Set<CitationKey> seenCitations = new LinkedHashSet<>();
        for (SearchResultResponse result : searchResponse.results()) {
            if (sources.size() >= normalizedRequest.maxSources()) {
                break;
            }

            Optional<AssistantContextSourceResponse> source = toContextSource(
                    result,
                    searchResponse.workspaceIdFilter(),
                    normalizedRequest.contextWindow()
            );
            source.ifPresent(contextSource -> {
                CitationKey citationKey = new CitationKey(
                        contextSource.assetId(),
                        contextSource.transcriptRowId(),
                        contextSource.segmentIndex()
                );
                if (seenCitations.add(citationKey)) {
                    sources.add(contextSource);
                }
            });
        }

        return new AssistantContextResponse(
                searchResponse.workspaceIdFilter(),
                normalizedRequest.query(),
                List.copyOf(sources)
        );
    }

    private Optional<AssistantContextSourceResponse> toContextSource(
            SearchResultResponse result,
            UUID workspaceId,
            int contextWindow
    ) {
        String transcriptRowId = resolveTranscriptRowId(result.transcriptRowId(), result.segmentIndex());
        if (result.assetId() == null || !StringUtils.hasText(transcriptRowId)) {
            return Optional.empty();
        }

        Asset asset;
        try {
            asset = assetService.getAsset(result.assetId());
        } catch (AssetNotFoundException exception) {
            return Optional.empty();
        }
        if (!workspaceId.equals(asset.getWorkspaceId()) || asset.getStatus() != AssetStatus.SEARCHABLE) {
            return Optional.empty();
        }

        List<AssetTranscriptRowResponse> contextRows;
        Integer hitSegmentIndex;
        try {
            if (contextWindow == 0) {
                contextRows = loadSingleHitRow(result.assetId(), transcriptRowId);
                hitSegmentIndex = contextRows.get(0).segmentIndex();
            } else {
                AssetTranscriptContextResponse contextResponse = assetService.getAssetTranscriptContext(
                        result.assetId(),
                        transcriptRowId,
                        contextWindow
                );
                contextRows = contextResponse.rows();
                hitSegmentIndex = contextResponse.hitSegmentIndex();
            }
        } catch (TranscriptRowNotFoundException | TranscriptUnavailableException exception) {
            return Optional.empty();
        }

        AssetTranscriptRowResponse hitRow = findHitRow(contextRows, transcriptRowId, hitSegmentIndex)
                .orElseGet(() -> contextRows.get(0));
        String stableTranscriptRowId = resolveTranscriptRowId(hitRow.id(), hitRow.segmentIndex());
        if (!StringUtils.hasText(stableTranscriptRowId)) {
            return Optional.empty();
        }

        return Optional.of(new AssistantContextSourceResponse(
                asset.getId(),
                asset.getTitle(),
                stableTranscriptRowId,
                hitRow.segmentIndex(),
                hitRow.createdAt(),
                boundText(joinContextText(contextRows)),
                new AssistantCitationResponse(asset.getId(), stableTranscriptRowId, hitRow.segmentIndex())
        ));
    }

    private List<AssetTranscriptRowResponse> loadSingleHitRow(UUID assetId, String transcriptRowId) {
        List<AssetTranscriptRowResponse> sortedRows = new ArrayList<>(assetService.getAssetTranscript(assetId));
        sortedRows.sort(Comparator.comparing(
                AssetTranscriptRowResponse::segmentIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));
        return sortedRows.stream()
                .filter(row -> matchesTranscriptRow(row, transcriptRowId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new TranscriptRowNotFoundException(assetId, transcriptRowId));
    }

    private Optional<AssetTranscriptRowResponse> findHitRow(
            List<AssetTranscriptRowResponse> rows,
            String transcriptRowId,
            Integer hitSegmentIndex
    ) {
        return rows.stream()
                .filter(row -> matchesTranscriptRow(row, transcriptRowId)
                        || (hitSegmentIndex != null && hitSegmentIndex.equals(row.segmentIndex())))
                .findFirst();
    }

    private boolean matchesTranscriptRow(AssetTranscriptRowResponse row, String transcriptRowId) {
        if (StringUtils.hasText(row.id())) {
            return row.id().equals(transcriptRowId);
        }
        return row.segmentIndex() != null
                && ("segment-" + row.segmentIndex()).equals(transcriptRowId);
    }

    private String joinContextText(List<AssetTranscriptRowResponse> rows) {
        return rows.stream()
                .map(AssetTranscriptRowResponse::text)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String boundText(String text) {
        if (text.length() <= MAX_SOURCE_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_SOURCE_TEXT_LENGTH);
    }

    private String resolveTranscriptRowId(String transcriptRowId, Integer segmentIndex) {
        if (StringUtils.hasText(transcriptRowId)) {
            return transcriptRowId.trim();
        }
        if (segmentIndex != null) {
            return "segment-" + segmentIndex;
        }
        return null;
    }

    private NormalizedRequest normalize(AssistantContextRequest request) {
        if (request == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_CONTEXT_REQUEST",
                    "Request body is required"
            );
        }
        if (request.workspaceId() == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_WORKSPACE_ID",
                    "workspaceId is required"
            );
        }
        if (!StringUtils.hasText(request.query())) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUERY",
                    "query is required"
            );
        }

        String query = request.query().trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUERY",
                    "query must be at most " + MAX_QUERY_LENGTH + " characters"
            );
        }

        int maxSources = request.maxSources() == null ? DEFAULT_MAX_SOURCES : request.maxSources();
        if (maxSources < 1 || maxSources > MAX_SOURCES) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_MAX_SOURCES",
                    "maxSources must be between 1 and " + MAX_SOURCES
            );
        }

        int contextWindow = request.contextWindow() == null ? DEFAULT_CONTEXT_WINDOW : request.contextWindow();
        if (contextWindow < 0 || contextWindow > MAX_CONTEXT_WINDOW) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_CONTEXT_WINDOW",
                    "contextWindow must be between 0 and " + MAX_CONTEXT_WINDOW
            );
        }

        return new NormalizedRequest(
                request.workspaceId(),
                query,
                request.assetId(),
                maxSources,
                contextWindow
        );
    }

    private record NormalizedRequest(
            UUID workspaceId,
            String query,
            UUID assetId,
            int maxSources,
            int contextWindow
    ) {
    }

    private record CitationKey(UUID assetId, String transcriptRowId, Integer segmentIndex) {
    }
}
