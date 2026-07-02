package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantAnswerRequest;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantAnswerResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.FastApiAssistantSourceRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssistantAnswerService {

    private static final String PROVIDER_UNAVAILABLE_MESSAGE = "Assistant provider is unavailable";

    private final AssistantContextService assistantContextService;
    private final FastApiAssistantClient fastApiAssistantClient;

    public AssistantAnswerService(
            AssistantContextService assistantContextService,
            FastApiAssistantClient fastApiAssistantClient
    ) {
        this.assistantContextService = assistantContextService;
        this.fastApiAssistantClient = fastApiAssistantClient;
    }

    public AssistantAnswerResponse answer(AssistantAnswerRequest request) {
        NormalizedAnswerRequest normalizedRequest = normalize(request);
        AssistantContextResponse context = assistantContextService.buildContext(new AssistantContextRequest(
                normalizedRequest.workspaceId(),
                normalizedRequest.question(),
                normalizedRequest.assetId(),
                normalizedRequest.maxSources(),
                normalizedRequest.contextWindow()
        ));

        Map<String, AssistantContextSourceResponse> sourcesById = new LinkedHashMap<>();
        List<FastApiAssistantSourceRequest> internalSources = new ArrayList<>();
        for (AssistantContextSourceResponse source : context.sources()) {
            String sourceId = sourceIdFor(source);
            sourcesById.put(sourceId, source);
            internalSources.add(new FastApiAssistantSourceRequest(
                    sourceId,
                    source.assetId(),
                    source.assetTitle(),
                    source.transcriptRowId(),
                    source.segmentIndex(),
                    source.createdAt(),
                    source.text()
            ));
        }

        FastApiAssistantAnswerResponse providerResponse;
        try {
            providerResponse = fastApiAssistantClient.answer(new FastApiAssistantAnswerRequest(
                    context.query(),
                    internalSources
            ));
        } catch (RuntimeException exception) {
            throw new AssistantProviderUnavailableException(PROVIDER_UNAVAILABLE_MESSAGE, exception);
        }

        return toPublicResponse(providerResponse, sourcesById);
    }

    private AssistantAnswerResponse toPublicResponse(
            FastApiAssistantAnswerResponse providerResponse,
            Map<String, AssistantContextSourceResponse> sourcesById
    ) {
        if (providerResponse == null
                || !StringUtils.hasText(providerResponse.answer())
                || providerResponse.citedSourceIds() == null
                || providerResponse.insufficientContext() == null) {
            throw new AssistantProviderUnavailableException(PROVIDER_UNAVAILABLE_MESSAGE);
        }

        Set<String> citedSourceIds = new LinkedHashSet<>();
        for (String citedSourceId : providerResponse.citedSourceIds()) {
            if (!StringUtils.hasText(citedSourceId) || !sourcesById.containsKey(citedSourceId)) {
                throw new AssistantProviderUnavailableException(PROVIDER_UNAVAILABLE_MESSAGE);
            }
            citedSourceIds.add(citedSourceId);
        }

        if (!providerResponse.insufficientContext() && citedSourceIds.isEmpty()) {
            throw new AssistantProviderUnavailableException(PROVIDER_UNAVAILABLE_MESSAGE);
        }

        List<AssistantAnswerCitationResponse> citations = citedSourceIds.stream()
                .map(sourceId -> toCitation(sourceId, sourcesById.get(sourceId)))
                .toList();

        return new AssistantAnswerResponse(
                providerResponse.answer().trim(),
                citations,
                providerResponse.insufficientContext()
        );
    }

    private AssistantAnswerCitationResponse toCitation(String sourceId, AssistantContextSourceResponse source) {
        return new AssistantAnswerCitationResponse(
                sourceId,
                source.assetId(),
                source.assetTitle(),
                source.transcriptRowId(),
                source.segmentIndex(),
                source.createdAt()
        );
    }

    private String sourceIdFor(AssistantContextSourceResponse source) {
        String rawSourceId = "%s|%s|%s".formatted(
                source.assetId(),
                source.transcriptRowId(),
                source.segmentIndex()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawSourceId.getBytes(StandardCharsets.UTF_8));
            return "src-" + HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private NormalizedAnswerRequest normalize(AssistantAnswerRequest request) {
        if (request == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_ANSWER_REQUEST",
                    "Request body is required"
            );
        }
        if (request.workspaceId() == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_WORKSPACE_ID",
                    "workspaceId is required"
            );
        }
        if (!StringUtils.hasText(request.question())) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUESTION",
                    "question is required"
            );
        }

        String question = request.question().trim();
        if (question.length() > AssistantContextService.MAX_QUERY_LENGTH) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUESTION",
                    "question must be at most " + AssistantContextService.MAX_QUERY_LENGTH + " characters"
            );
        }

        return new NormalizedAnswerRequest(
                request.workspaceId(),
                question,
                request.assetId(),
                request.maxSources(),
                request.contextWindow()
        );
    }

    private record NormalizedAnswerRequest(
            java.util.UUID workspaceId,
            String question,
            java.util.UUID assetId,
            Integer maxSources,
            Integer contextWindow
    ) {
    }
}
