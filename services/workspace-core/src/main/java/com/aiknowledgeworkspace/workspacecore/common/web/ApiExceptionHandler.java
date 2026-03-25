package com.aiknowledgeworkspace.workspacecore.common.web;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiConnectivityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FastApiConnectivityException.class)
    public ResponseEntity<ApiErrorResponse> handleFastApiTimeout(FastApiConnectivityException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiErrorResponse("FASTAPI_TIMEOUT", exception.getMessage()));
    }

    @ExceptionHandler(FastApiIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleFastApiIntegration(FastApiIntegrationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("FASTAPI_INTEGRATION_ERROR", exception.getMessage()));
    }
}
