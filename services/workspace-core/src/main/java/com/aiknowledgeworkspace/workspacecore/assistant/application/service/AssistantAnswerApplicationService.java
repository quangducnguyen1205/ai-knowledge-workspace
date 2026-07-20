package com.aiknowledgeworkspace.workspacecore.assistant.application.service;

import com.aiknowledgeworkspace.workspacecore.assistant.application.exception.AssistantProviderUnavailableException;
import com.aiknowledgeworkspace.workspacecore.assistant.application.exception.InvalidAssistantContextRequestException;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantAnswerCitation;
import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantAnswerQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantAnswerResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextSource;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.in.AssistantAnswerUseCase;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantAnswerProviderPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantProviderRequest;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantProviderResponse;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantProviderSource;
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
public class AssistantAnswerApplicationService implements AssistantAnswerUseCase {

    private static final String PROVIDER_UNAVAILABLE_MESSAGE = "Assistant provider is unavailable";

    private final AssistantContextApplicationService assistantContextService;
    private final AssistantAnswerProviderPort assistantAnswerProviderPort;

    public AssistantAnswerApplicationService(
            AssistantContextApplicationService assistantContextService,
            AssistantAnswerProviderPort assistantAnswerProviderPort
    ) {
        this.assistantContextService = assistantContextService;
        this.assistantAnswerProviderPort = assistantAnswerProviderPort;
    }

    @Override
    public AssistantAnswerResult answer(AssistantAnswerQuery command) {
        NormalizedAnswerRequest normalizedRequest = normalize(command);
        AssistantContextResult context = assistantContextService.query(new AssistantContextQuery(
                normalizedRequest.workspaceId(),
                normalizedRequest.question(),
                normalizedRequest.assetId(),
                normalizedRequest.maxSources(),
                normalizedRequest.contextWindow()
        ));

        Map<String, AssistantContextSource> sourcesById = new LinkedHashMap<>();
        List<AssistantProviderSource> internalSources = new ArrayList<>();
        for (AssistantContextSource source : context.sources()) {
            String sourceId = sourceIdFor(source);
            sourcesById.put(sourceId, source);
            internalSources.add(new AssistantProviderSource(
                    sourceId,
                    source.assetId(),
                    source.assetTitle(),
                    source.transcriptRowId(),
                    source.segmentIndex(),
                    source.createdAt(),
                    source.text()
            ));
        }

        AssistantProviderResponse providerResponse;
        try {
            providerResponse = assistantAnswerProviderPort.answer(new AssistantProviderRequest(
                    context.query(),
                    internalSources
            ));
        } catch (RuntimeException exception) {
            throw new AssistantProviderUnavailableException(PROVIDER_UNAVAILABLE_MESSAGE, exception);
        }

        return toResult(providerResponse, sourcesById);
    }

    private AssistantAnswerResult toResult(
            AssistantProviderResponse providerResponse,
            Map<String, AssistantContextSource> sourcesById
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

        List<AssistantAnswerCitation> citations = citedSourceIds.stream()
                .map(sourceId -> toCitation(sourceId, sourcesById.get(sourceId)))
                .toList();

        return new AssistantAnswerResult(
                providerResponse.answer().trim(),
                citations,
                providerResponse.insufficientContext()
        );
    }

    private AssistantAnswerCitation toCitation(String sourceId, AssistantContextSource source) {
        return new AssistantAnswerCitation(
                sourceId,
                source.assetId(),
                source.assetTitle(),
                source.transcriptRowId(),
                source.segmentIndex(),
                source.createdAt()
        );
    }

    private String sourceIdFor(AssistantContextSource source) {
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

    private NormalizedAnswerRequest normalize(AssistantAnswerQuery command) {
        if (command == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_ANSWER_REQUEST",
                    "Request body is required"
            );
        }
        if (command.workspaceId() == null) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_WORKSPACE_ID",
                    "workspaceId is required"
            );
        }
        if (!StringUtils.hasText(command.question())) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUESTION",
                    "question is required"
            );
        }

        String question = command.question().trim();
        if (question.length() > AssistantContextApplicationService.MAX_QUERY_LENGTH) {
            throw new InvalidAssistantContextRequestException(
                    "INVALID_ASSISTANT_QUESTION",
                    "question must be at most " + AssistantContextApplicationService.MAX_QUERY_LENGTH + " characters"
            );
        }

        return new NormalizedAnswerRequest(
                command.workspaceId(),
                question,
                command.assetId(),
                command.maxSources(),
                command.contextWindow()
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
