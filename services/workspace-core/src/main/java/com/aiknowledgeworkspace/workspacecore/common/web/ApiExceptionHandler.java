package com.aiknowledgeworkspace.workspacecore.common.web;

import com.aiknowledgeworkspace.workspacecore.asset.AssetListRequestException;
import com.aiknowledgeworkspace.workspacecore.asset.InvalidTranscriptContextWindowException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.TranscriptRowNotFoundException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;
import com.aiknowledgeworkspace.workspacecore.workspace.InvalidWorkspaceNameException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FastApiConnectivityException.class)
    public ResponseEntity<ApiErrorResponse> handleFastApiConnectivity(FastApiConnectivityException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiErrorResponse("FASTAPI_CONNECTIVITY_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(FastApiIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleFastApiIntegration(FastApiIntegrationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("FASTAPI_INTEGRATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(ElasticsearchConnectivityException.class)
    public ResponseEntity<ApiErrorResponse> handleElasticsearchConnectivity(
            ElasticsearchConnectivityException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("ELASTICSEARCH_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(ElasticsearchIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleElasticsearchIntegration(
            ElasticsearchIntegrationException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("ELASTICSEARCH_INTEGRATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceNotFound(WorkspaceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("WORKSPACE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InvalidWorkspaceNameException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidWorkspaceName(InvalidWorkspaceNameException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_WORKSPACE_NAME", exception.getMessage()));
    }

    @ExceptionHandler(InvalidTranscriptContextWindowException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTranscriptContextWindow(
            InvalidTranscriptContextWindowException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_TRANSCRIPT_CONTEXT_WINDOW", exception.getMessage()));
    }

    @ExceptionHandler(TranscriptRowNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTranscriptRowNotFound(TranscriptRowNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("TRANSCRIPT_ROW_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(AssetListRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleAssetListRequest(AssetListRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        String errorCode;
        String message;
        if ("workspaceId".equals(exception.getName())) {
            errorCode = "INVALID_WORKSPACE_ID";
            message = "workspaceId must be a valid UUID";
        } else if ("window".equals(exception.getName())) {
            errorCode = "INVALID_TRANSCRIPT_CONTEXT_WINDOW";
            message = "window must be a valid integer";
        } else if ("page".equals(exception.getName())) {
            errorCode = "INVALID_ASSET_PAGE";
            message = "page must be a valid integer";
        } else if ("size".equals(exception.getName())) {
            errorCode = "INVALID_ASSET_SIZE";
            message = "size must be a valid integer";
        } else if ("assetStatus".equals(exception.getName())
                && AssetStatus.class.equals(exception.getRequiredType())) {
            errorCode = "INVALID_ASSET_STATUS";
            message = "assetStatus must be one of: PROCESSING, TRANSCRIPT_READY, SEARCHABLE, FAILED";
        } else {
            errorCode = "INVALID_REQUEST_PARAMETER";
            message = "Invalid value for request parameter " + exception.getName();
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(errorCode, message));
    }
}
