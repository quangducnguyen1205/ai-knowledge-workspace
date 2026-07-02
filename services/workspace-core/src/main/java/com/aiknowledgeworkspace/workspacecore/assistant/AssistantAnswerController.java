package com.aiknowledgeworkspace.workspacecore.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantAnswerController {

    private final AssistantAnswerService assistantAnswerService;

    public AssistantAnswerController(AssistantAnswerService assistantAnswerService) {
        this.assistantAnswerService = assistantAnswerService;
    }

    @PostMapping("/answer")
    public AssistantAnswerResponse answer(@RequestBody(required = false) AssistantAnswerRequest request) {
        return assistantAnswerService.answer(request);
    }
}
