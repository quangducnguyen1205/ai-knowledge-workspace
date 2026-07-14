package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingConnectivityException;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AssetApiExceptionHandler {

    @ExceptionHandler(AssetNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleAssetNotFound(AssetNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ProcessingJobNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleProcessingJobNotFound(ProcessingJobNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "PROCESSING_JOB_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidAssetTitleException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAssetTitle(InvalidAssetTitleException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ASSET_TITLE", exception.getMessage());
    }

    @ExceptionHandler(InvalidUploadRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidUploadRequest(InvalidUploadRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_FILE", exception.getMessage());
    }

    @ExceptionHandler(InvalidTranscriptContextWindowException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidTranscriptContextWindow(
            InvalidTranscriptContextWindowException exception
    ) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_TRANSCRIPT_CONTEXT_WINDOW", exception.getMessage());
    }

    @ExceptionHandler(TranscriptUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleTranscriptUnavailable(TranscriptUnavailableException exception) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(TranscriptRowNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleTranscriptRowNotFound(TranscriptRowNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "TRANSCRIPT_ROW_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AssetListRequestException.class)
    ResponseEntity<ApiErrorResponse> handleAssetListRequest(AssetListRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(DirectProcessingConnectivityException.class)
    ResponseEntity<ApiErrorResponse> handleDirectProcessingConnectivity(DirectProcessingConnectivityException exception) {
        return response(HttpStatus.GATEWAY_TIMEOUT, "FASTAPI_CONNECTIVITY_ERROR", exception.getMessage());
    }

    @ExceptionHandler(DirectProcessingIntegrationException.class)
    ResponseEntity<ApiErrorResponse> handleDirectProcessingIntegration(DirectProcessingIntegrationException exception) {
        return response(HttpStatus.BAD_GATEWAY, "FASTAPI_INTEGRATION_ERROR", exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
