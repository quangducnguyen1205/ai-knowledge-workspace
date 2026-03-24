package com.aiknowledgeworkspace.workspacecore.common.web;

public record ApiErrorResponse(
        String code,
        String message
) {
}
