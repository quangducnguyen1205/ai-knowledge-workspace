package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WorkspaceApiExceptionHandler {

    @ExceptionHandler(WorkspaceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleWorkspaceNotFound(WorkspaceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidWorkspaceNameException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidWorkspaceName(InvalidWorkspaceNameException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_WORKSPACE_NAME", exception.getMessage());
    }

    @ExceptionHandler(WorkspaceDeleteConflictException.class)
    ResponseEntity<ApiErrorResponse> handleWorkspaceDeleteConflict(WorkspaceDeleteConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(DefaultWorkspaceConflictException.class)
    ResponseEntity<ApiErrorResponse> handleDefaultWorkspaceConflict(DefaultWorkspaceConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
