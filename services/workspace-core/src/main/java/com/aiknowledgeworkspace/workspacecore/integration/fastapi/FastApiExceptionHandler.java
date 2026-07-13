package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FastApiExceptionHandler {

    @ExceptionHandler(FastApiConnectivityException.class)
    ResponseEntity<ApiErrorResponse> handleFastApiConnectivity(FastApiConnectivityException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiErrorResponse("FASTAPI_CONNECTIVITY_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(FastApiIntegrationException.class)
    ResponseEntity<ApiErrorResponse> handleFastApiIntegration(FastApiIntegrationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("FASTAPI_INTEGRATION_ERROR", exception.getMessage()));
    }
}
