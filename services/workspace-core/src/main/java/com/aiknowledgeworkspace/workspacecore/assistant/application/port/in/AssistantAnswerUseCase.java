package com.aiknowledgeworkspace.workspacecore.assistant.application.port.in;

import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantAnswerQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantAnswerResult;

public interface AssistantAnswerUseCase {

    AssistantAnswerResult answer(AssistantAnswerQuery command);
}
