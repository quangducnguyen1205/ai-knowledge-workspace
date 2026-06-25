package com.aiknowledgeworkspace.workspacecore.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantContextController {

    private final AssistantContextService assistantContextService;

    public AssistantContextController(AssistantContextService assistantContextService) {
        this.assistantContextService = assistantContextService;
    }

    @PostMapping("/context")
    public AssistantContextResponse buildContext(@RequestBody(required = false) AssistantContextRequest request) {
        return assistantContextService.buildContext(request);
    }
}
