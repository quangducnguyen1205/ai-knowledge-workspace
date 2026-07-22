package com.aiknowledgeworkspace.workspacecore.assistant.application.service;

import com.aiknowledgeworkspace.workspacecore.assistant.application.exception.InvalidAssistantContextRequestException;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantCitation;
import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextSource;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.in.AssistantContextQueryUseCase;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchHit;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantTranscriptContextPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantTranscriptSegment;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssistantContextApplicationService implements AssistantContextQueryUseCase {

    public static final int DEFAULT_MAX_SOURCES = 5;
    public static final int MAX_SOURCES = 10;
    public static final int DEFAULT_CONTEXT_WINDOW = 1;
    public static final int MAX_CONTEXT_WINDOW = 5;
    public static final int MAX_QUERY_LENGTH = 500;
    public static final int MAX_SOURCE_TEXT_LENGTH = 2_000;

    private final AssistantSearchPort searchPort;
    private final AssistantTranscriptContextPort transcriptContextPort;

    public AssistantContextApplicationService(
            AssistantSearchPort searchPort,
            AssistantTranscriptContextPort transcriptContextPort
    ) {
        this.searchPort = searchPort;
        this.transcriptContextPort = transcriptContextPort;
    }

    @Override
    public AssistantContextResult query(AssistantContextQuery query) {
        NormalizedRequest normalizedRequest = normalize(query);
        AssistantSearchPage searchResponse = searchPort.search(
                normalizedRequest.query(),
                normalizedRequest.workspaceId(),
                normalizedRequest.assetId()
        );

        List<AssistantContextSource> sources = new ArrayList<>();
        Set<CitationKey> seenCitations = new LinkedHashSet<>();
        for (AssistantSearchHit result : searchResponse.results()) {
            if (sources.size() >= normalizedRequest.maxSources()) {
                break;
            }

            Optional<AssistantContextSource> source = toContextSource(
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

        return new AssistantContextResult(
                searchResponse.workspaceIdFilter(),
                normalizedRequest.query(),
                List.copyOf(sources)
        );
    }

    private Optional<AssistantContextSource> toContextSource(
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

        return Optional.of(new AssistantContextSource(
                contextValue.assetId(),
                contextValue.assetTitle(),
                stableTranscriptRowId,
                hitRow.segmentIndex(),
                hitRow.startMs(),
                hitRow.endMs(),
                hitRow.createdAt(),
                boundText(joinContextText(contextRows)),
                new AssistantCitation(
                        contextValue.assetId(), stableTranscriptRowId, hitRow.segmentIndex(),
                        hitRow.startMs(), hitRow.endMs()
                )
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

    private NormalizedRequest normalize(AssistantContextQuery query) {
        if (query == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_CONTEXT_REQUEST",
                    "Request body is required"
            );
        }
        if (query.workspaceId() == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_WORKSPACE_ID",
                    "workspaceId is required"
            );
        }
        if (!StringUtils.hasText(query.query())) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUERY",
                    "query is required"
            );
        }

        String normalizedQuery = query.query().trim();
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUERY",
                    "query must be at most " + MAX_QUERY_LENGTH + " characters"
            );
        }

        int maxSources = query.maxSources() == null ? DEFAULT_MAX_SOURCES : query.maxSources();
        if (maxSources < 1 || maxSources > MAX_SOURCES) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_MAX_SOURCES",
                    "maxSources must be between 1 and " + MAX_SOURCES
            );
        }

        int contextWindow = query.contextWindow() == null ? DEFAULT_CONTEXT_WINDOW : query.contextWindow();
        if (contextWindow < 0 || contextWindow > MAX_CONTEXT_WINDOW) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_CONTEXT_WINDOW",
                    "contextWindow must be between 0 and " + MAX_CONTEXT_WINDOW
            );
        }

        return new NormalizedRequest(
                query.workspaceId(),
                normalizedQuery,
                query.assetId(),
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
