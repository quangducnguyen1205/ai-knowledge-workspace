package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SearchApiExceptionHandler {

    @ExceptionHandler(SearchIndexConnectivityException.class)
    ResponseEntity<ApiErrorResponse> handleElasticsearchConnectivity(SearchIndexConnectivityException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("ELASTICSEARCH_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(SearchIndexOperationException.class)
    ResponseEntity<ApiErrorResponse> handleElasticsearchIntegration(SearchIndexOperationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("ELASTICSEARCH_INTEGRATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSearchRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidSearchRequest(InvalidSearchRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(SearchProcessingJobNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleProcessingJobNotFound(SearchProcessingJobNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("PROCESSING_JOB_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(SearchTranscriptUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleTranscriptUnavailable(SearchTranscriptUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(SearchAssetNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleAssetNotFound(SearchAssetNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("ASSET_NOT_FOUND", exception.getMessage()));
    }
}
