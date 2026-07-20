package com.aiknowledgeworkspace.workspacecore.workspace.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.WorkspaceDeleteConflictException;

import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.WorkspaceNotFoundException;

import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.InvalidWorkspaceNameException;

import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.DefaultWorkspaceConflictException;

import com.aiknowledgeworkspace.workspacecore.common.web.api.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.api.PublicApiErrorResponses;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceApiExceptionHandler {

    @ExceptionHandler(WorkspaceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleWorkspaceNotFound(WorkspaceNotFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace not found", exception
        );
    }

    @ExceptionHandler(InvalidWorkspaceNameException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidWorkspaceName(InvalidWorkspaceNameException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_WORKSPACE_NAME", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(WorkspaceDeleteConflictException.class)
    ResponseEntity<ApiErrorResponse> handleWorkspaceDeleteConflict(WorkspaceDeleteConflictException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, exception.getCode(), safeWorkspaceConflictMessage(exception.getCode()), exception
        );
    }

    @ExceptionHandler(DefaultWorkspaceConflictException.class)
    ResponseEntity<ApiErrorResponse> handleDefaultWorkspaceConflict(DefaultWorkspaceConflictException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, exception.getCode(), safeWorkspaceConflictMessage(exception.getCode()), exception
        );
    }

    private String safeWorkspaceConflictMessage(String code) {
        return switch (code) {
            case "DEFAULT_WORKSPACE_DELETE_FORBIDDEN" -> "Default workspace cannot be deleted";
            case "WORKSPACE_NOT_EMPTY" -> "Workspace cannot be deleted while it still contains assets";
            default -> "Workspace setup could not be completed";
        };
    }
}
