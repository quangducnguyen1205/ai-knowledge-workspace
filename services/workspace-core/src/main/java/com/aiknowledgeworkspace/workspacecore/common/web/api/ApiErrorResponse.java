package com.aiknowledgeworkspace.workspacecore.common.web.api;

public record ApiErrorResponse(
        String code,
        String message
) {
}
