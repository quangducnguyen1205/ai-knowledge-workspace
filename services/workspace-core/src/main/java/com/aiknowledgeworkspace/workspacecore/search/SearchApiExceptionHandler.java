package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.PublicApiErrorResponses;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SearchApiExceptionHandler {

    @ExceptionHandler(SearchIndexConnectivityException.class)
    ResponseEntity<ApiErrorResponse> handleElasticsearchConnectivity(SearchIndexConnectivityException exception) {
        return PublicApiErrorResponses.serviceUnavailable("SEARCH_SERVICE_UNAVAILABLE", exception);
    }

    @ExceptionHandler(SearchIndexOperationException.class)
    ResponseEntity<ApiErrorResponse> handleElasticsearchIntegration(SearchIndexOperationException exception) {
        return PublicApiErrorResponses.serviceUnavailable("SEARCH_SERVICE_UNAVAILABLE", exception);
    }

    @ExceptionHandler(InvalidSearchRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidSearchRequest(InvalidSearchRequestException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), exception
        );
    }

    @ExceptionHandler(SearchProcessingJobNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleProcessingJobNotFound(SearchProcessingJobNotFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "PROCESSING_JOB_NOT_FOUND", "Processing job not found", exception
        );
    }

    @ExceptionHandler(SearchTranscriptUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleTranscriptUnavailable(SearchTranscriptUnavailableException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), exception
        );
    }

    @ExceptionHandler(SearchAssetNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleAssetNotFound(SearchAssetNotFoundException exception) {
        return PublicApiErrorResponses.clientError(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found", exception);
    }
}
