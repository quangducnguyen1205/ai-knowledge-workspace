package com.aiknowledgeworkspace.workspacecore.assistant.application.port.in;

import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextResult;

public interface AssistantContextQueryUseCase {

    AssistantContextResult query(AssistantContextQuery query);
}
