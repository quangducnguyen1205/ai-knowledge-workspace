package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.assistant.application.exception.InvalidAssistantContextRequestException;

import com.aiknowledgeworkspace.workspacecore.assistant.application.exception.AssistantProviderUnavailableException;

import com.aiknowledgeworkspace.workspacecore.common.web.api.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.api.PublicApiErrorResponses;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssistantApiExceptionHandler {

    @ExceptionHandler(AssistantProviderUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleAssistantProviderUnavailable(
            AssistantProviderUnavailableException exception
    ) {
        return PublicApiErrorResponses.serviceUnavailable("ASSISTANT_SERVICE_UNAVAILABLE", exception);
    }

    @ExceptionHandler(InvalidAssistantContextRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAssistantContextRequest(
            InvalidAssistantContextRequestException exception
    ) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), exception
        );
    }
}
