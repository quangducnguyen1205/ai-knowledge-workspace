package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AssistantApiExceptionHandler {

    @ExceptionHandler(AssistantProviderUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleAssistantProviderUnavailable(
            AssistantProviderUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("ASSISTANT_PROVIDER_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(InvalidAssistantContextRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAssistantContextRequest(
            InvalidAssistantContextRequestException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }
}
