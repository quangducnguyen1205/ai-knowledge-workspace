package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.in.AssistantContextQueryUseCase;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantContextController {

    private final AssistantContextQueryUseCase contextQueries;

    public AssistantContextController(AssistantContextQueryUseCase contextQueries) {
        this.contextQueries = contextQueries;
    }

    @PostMapping("/context")
    public AssistantContextResponse buildContext(@RequestBody(required = false) AssistantContextRequest request) {
        AssistantContextResult result = contextQueries.query(request == null ? null : new AssistantContextQuery(
                request.workspaceId(),
                request.query(),
                request.assetId(),
                request.maxSources(),
                request.contextWindow()
        ));
        return new AssistantContextResponse(
                result.workspaceId(),
                result.query(),
                result.sources().stream()
                        .map(source -> new AssistantContextSourceResponse(
                                source.assetId(),
                                source.assetTitle(),
                                source.transcriptRowId(),
                                source.segmentIndex(),
                                source.createdAt(),
                                source.text(),
                                new AssistantCitationResponse(
                                        source.citation().assetId(),
                                        source.citation().transcriptRowId(),
                                        source.citation().segmentIndex()
                                )
                        ))
                        .toList()
        );
    }
}
