package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.ProcessingJobNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetListRequestException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptRowNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidAssetTitleException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidUploadRequestException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidYouTubeUrlException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetProcessingRetryNotAllowedException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidTranscriptContextWindowException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaNotAvailableException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaReadException;

import com.aiknowledgeworkspace.workspacecore.common.web.api.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.api.PublicApiErrorResponses;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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

    @ExceptionHandler(InvalidYouTubeUrlException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidYouTubeUrl(InvalidYouTubeUrlException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_YOUTUBE_URL", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(DuplicateYouTubeAssetException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateYouTubeAsset(DuplicateYouTubeAssetException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, "DUPLICATE_YOUTUBE_ASSET", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(AssetProcessingRetryNotAllowedException.class)
    ResponseEntity<ApiErrorResponse> handleRetryNotAllowed(AssetProcessingRetryNotAllowedException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, "ASSET_PROCESSING_RETRY_NOT_ALLOWED", exception.getMessage(), exception
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

    @ExceptionHandler(AssetMediaNotAvailableException.class)
    ResponseEntity<ApiErrorResponse> handleAssetMediaNotAvailable(AssetMediaNotAvailableException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND,
                "ASSET_MEDIA_NOT_AVAILABLE",
                "Asset media is not available",
                exception
        );
    }

    @ExceptionHandler(AssetMediaRangeNotSatisfiableException.class)
    ResponseEntity<ApiErrorResponse> handleAssetMediaRangeNotSatisfiable(
            AssetMediaRangeNotSatisfiableException exception
    ) {
        ResponseEntity<ApiErrorResponse> response = PublicApiErrorResponses.clientError(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "ASSET_MEDIA_RANGE_NOT_SATISFIABLE",
                "Requested media range is not satisfiable",
                exception
        );
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + exception.getTotalSizeBytes())
                .body(response.getBody());
    }

    @ExceptionHandler(AssetMediaReadException.class)
    ResponseEntity<ApiErrorResponse> handleAssetMediaRead(AssetMediaReadException exception) {
        return PublicApiErrorResponses.serviceUnavailable("ASSET_MEDIA_READ_FAILED", exception);
    }

}
