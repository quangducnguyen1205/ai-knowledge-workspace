package com.aiknowledgeworkspace.workspacecore.asset;

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
public class AssetApiExceptionHandler {

    @ExceptionHandler(AssetNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleAssetNotFound(AssetNotFoundException exception) {
        return PublicApiErrorResponses.clientError(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found", exception);
    }

    @ExceptionHandler(ProcessingJobNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleProcessingJobNotFound(ProcessingJobNotFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "PROCESSING_JOB_NOT_FOUND", "Processing job not found", exception
        );
    }

    @ExceptionHandler(InvalidAssetTitleException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAssetTitle(InvalidAssetTitleException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_ASSET_TITLE", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(InvalidUploadRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidUploadRequest(InvalidUploadRequestException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_FILE", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(InvalidTranscriptContextWindowException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidTranscriptContextWindow(
            InvalidTranscriptContextWindowException exception
    ) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_TRANSCRIPT_CONTEXT_WINDOW", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(TranscriptUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleTranscriptUnavailable(TranscriptUnavailableException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), exception
        );
    }

    @ExceptionHandler(TranscriptRowNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleTranscriptRowNotFound(TranscriptRowNotFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "TRANSCRIPT_ROW_NOT_FOUND", "Transcript row not found", exception
        );
    }

    @ExceptionHandler(AssetListRequestException.class)
    ResponseEntity<ApiErrorResponse> handleAssetListRequest(AssetListRequestException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), exception
        );
    }

}
