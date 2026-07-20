package com.aiknowledgeworkspace.workspacecore.assistant.application.port.in;

import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerCommand;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerResult;

public interface AssistantAnswerCommandUseCase {

    AssistantAnswerResult answer(AssistantAnswerCommand command);
}
