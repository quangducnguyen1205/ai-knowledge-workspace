package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptContextPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantTranscriptSegment;
import java.util.ArrayList;
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

    private final AssistantSearchPort searchPort;
    private final AssistantTranscriptContextPort transcriptContextPort;

    public AssistantContextService(
            AssistantSearchPort searchPort,
            AssistantTranscriptContextPort transcriptContextPort
    ) {
        this.searchPort = searchPort;
        this.transcriptContextPort = transcriptContextPort;
    }

    public AssistantContextResponse buildContext(AssistantContextRequest request) {
        NormalizedRequest normalizedRequest = normalize(request);
        AssistantSearchPage searchResponse = searchPort.search(
                normalizedRequest.query(),
                normalizedRequest.workspaceId(),
                normalizedRequest.assetId()
        );

        List<AssistantContextSourceResponse> sources = new ArrayList<>();
        Set<CitationKey> seenCitations = new LinkedHashSet<>();
        for (AssistantSearchHit result : searchResponse.results()) {
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
            AssistantSearchHit result,
            UUID workspaceId,
            int contextWindow
    ) {
        String transcriptRowId = resolveTranscriptRowId(result.transcriptRowId(), result.segmentIndex());
        if (result.assetId() == null || !StringUtils.hasText(transcriptRowId)) {
            return Optional.empty();
        }

        Optional<AssistantTranscriptContext> context = transcriptContextPort.findSearchableTranscriptContext(
                result.assetId(),
                workspaceId,
                transcriptRowId,
                contextWindow
        );
        if (context.isEmpty()) {
            return Optional.empty();
        }

        AssistantTranscriptContext contextValue = context.get();
        List<AssistantTranscriptSegment> contextRows = contextValue.rows();
        AssistantTranscriptSegment hitRow = findHitRow(contextRows, transcriptRowId, contextValue.hitSegmentIndex())
                .orElseGet(() -> contextRows.get(0));
        String stableTranscriptRowId = resolveTranscriptRowId(hitRow.id(), hitRow.segmentIndex());
        if (!StringUtils.hasText(stableTranscriptRowId)) {
            return Optional.empty();
        }

        return Optional.of(new AssistantContextSourceResponse(
                contextValue.assetId(),
                contextValue.assetTitle(),
                stableTranscriptRowId,
                hitRow.segmentIndex(),
                hitRow.createdAt(),
                boundText(joinContextText(contextRows)),
                new AssistantCitationResponse(contextValue.assetId(), stableTranscriptRowId, hitRow.segmentIndex())
        ));
    }

    private Optional<AssistantTranscriptSegment> findHitRow(
            List<AssistantTranscriptSegment> rows,
            String transcriptRowId,
            Integer hitSegmentIndex
    ) {
        return rows.stream()
                .filter(row -> matchesTranscriptRow(row, transcriptRowId)
                        || (hitSegmentIndex != null && hitSegmentIndex.equals(row.segmentIndex())))
                .findFirst();
    }

    private boolean matchesTranscriptRow(AssistantTranscriptSegment row, String transcriptRowId) {
        if (StringUtils.hasText(row.id())) {
            return row.id().equals(transcriptRowId);
        }
        return row.segmentIndex() != null
                && ("segment-" + row.segmentIndex()).equals(transcriptRowId);
    }

    private String joinContextText(List<AssistantTranscriptSegment> rows) {
        return rows.stream()
                .map(AssistantTranscriptSegment::text)
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
