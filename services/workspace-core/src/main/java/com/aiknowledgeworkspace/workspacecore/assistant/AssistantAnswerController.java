package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerCommand;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.in.AssistantAnswerCommandUseCase;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantAnswerController {

    private final AssistantAnswerCommandUseCase answerCommands;

    public AssistantAnswerController(AssistantAnswerCommandUseCase answerCommands) {
        this.answerCommands = answerCommands;
    }

    @PostMapping("/answer")
    public AssistantAnswerResponse answer(@RequestBody(required = false) AssistantAnswerRequest request) {
        AssistantAnswerResult result = answerCommands.answer(request == null ? null : new AssistantAnswerCommand(
                request.workspaceId(),
                request.question(),
                request.assetId(),
                request.maxSources(),
                request.contextWindow()
        ));
        return new AssistantAnswerResponse(
                result.answer(),
                result.citations().stream()
                        .map(citation -> new AssistantAnswerCitationResponse(
                                citation.sourceId(),
                                citation.assetId(),
                                citation.assetTitle(),
                                citation.transcriptRowId(),
                                citation.segmentIndex(),
                                citation.createdAt()
                        ))
                        .toList(),
                result.insufficientContext()
        );
    }
}
