package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SearchApiExceptionHandler {

    @ExceptionHandler(ElasticsearchConnectivityException.class)
    ResponseEntity<ApiErrorResponse> handleElasticsearchConnectivity(ElasticsearchConnectivityException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("ELASTICSEARCH_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(ElasticsearchIntegrationException.class)
    ResponseEntity<ApiErrorResponse> handleElasticsearchIntegration(ElasticsearchIntegrationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("ELASTICSEARCH_INTEGRATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSearchRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidSearchRequest(InvalidSearchRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }
}
