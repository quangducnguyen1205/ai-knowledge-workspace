package com.aiknowledgeworkspace.workspacecore.assistant.application.port.in;

import com.aiknowledgeworkspace.workspacecore.assistant.application.query.AssistantContextQuery;
import com.aiknowledgeworkspace.workspacecore.assistant.application.result.AssistantContextResult;

public interface AssistantContextQueryUseCase {

    AssistantContextResult query(AssistantContextQuery query);
}
