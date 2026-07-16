package com.aiknowledgeworkspace.workspacecore.common.web;

import com.aiknowledgeworkspace.workspacecore.common.identity.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.common.identity.AuthModeUnavailableException;
import com.aiknowledgeworkspace.workspacecore.common.identity.EmailAlreadyRegisteredException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidAuthRequestException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidCredentialsException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidCurrentUserIdException;
import com.aiknowledgeworkspace.workspacecore.common.identity.InvalidJwtIdentityException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCurrentUserIdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCurrentUserId(InvalidCurrentUserIdException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_CURRENT_USER_ID", "userId is required", exception
        );
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAuthRequest(InvalidAuthRequestException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), exception
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered", exception
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect", exception
        );
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required", exception
        );
    }

    @ExceptionHandler(AuthModeUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthModeUnavailable(AuthModeUnavailableException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.CONFLICT,
                "AUTH_MODE_UNAVAILABLE",
                "The selected authentication mode is unavailable",
                exception
        );
    }

    @ExceptionHandler(InvalidJwtIdentityException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJwtIdentity(InvalidJwtIdentityException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required", exception
        );
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

        return PublicApiErrorResponses.clientError(HttpStatus.BAD_REQUEST, errorCode, message, exception);
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

        return PublicApiErrorResponses.clientError(HttpStatus.BAD_REQUEST, errorCode, message, exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or malformed",
                exception
        );
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

        return PublicApiErrorResponses.clientError(HttpStatus.BAD_REQUEST, errorCode, message, exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "This request method is not supported",
                exception
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "This request content type is not supported",
                exception
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", exception
        );
    }

}
