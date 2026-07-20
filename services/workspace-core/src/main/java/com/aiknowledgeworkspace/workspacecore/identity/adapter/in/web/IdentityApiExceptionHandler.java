package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.common.web.api.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.api.PublicApiErrorResponses;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthModeUnavailableException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.EmailAlreadyRegisteredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidAuthRequestException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidCredentialsException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidCurrentUserIdException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidJwtIdentityException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityApiExceptionHandler {

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
}
