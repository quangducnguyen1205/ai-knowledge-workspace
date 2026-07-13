package com.aiknowledgeworkspace.workspacecore.common.web;

import com.aiknowledgeworkspace.workspacecore.common.identity.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.common.identity.AuthModeUnavailableException;
import com.aiknowledgeworkspace.workspacecore.common.identity.EmailAlreadyRegisteredException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidAuthRequestException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidCredentialsException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidCurrentUserIdException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCurrentUserIdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCurrentUserId(InvalidCurrentUserIdException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_CURRENT_USER_ID", exception.getMessage()));
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAuthRequest(InvalidAuthRequestException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("EMAIL_ALREADY_REGISTERED", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("INVALID_CREDENTIALS", exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("AUTHENTICATION_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(AuthModeUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthModeUnavailable(AuthModeUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("AUTH_MODE_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        String errorCode;
        String message;
        if ("q".equals(exception.getParameterName())) {
            errorCode = "INVALID_SEARCH_QUERY";
            message = "q query parameter is required";
        } else {
            errorCode = "INVALID_REQUEST_PARAMETER";
            message = "Missing required request parameter " + exception.getParameterName();
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(errorCode, message));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestPart(
            MissingServletRequestPartException exception
    ) {
        String errorCode;
        String message;
        if ("file".equals(exception.getRequestPartName())) {
            errorCode = "INVALID_UPLOAD_FILE";
            message = "file is required";
        } else {
            errorCode = "INVALID_REQUEST_BODY";
            message = "Required request part " + exception.getRequestPartName() + " is missing";
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(errorCode, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_REQUEST_BODY", "Request body is missing or malformed"));
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
        } else if ("assetStatus".equals(exception.getName())) {
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
