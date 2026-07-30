package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.common.web.api.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.api.PublicApiErrorResponses;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.InvalidSavedMomentRequestException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentTargetNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SavedMomentApiExceptionHandler {

    @ExceptionHandler(InvalidSavedMomentRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidSavedMomentRequestException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.BAD_REQUEST, "INVALID_SAVED_MOMENT_REQUEST", exception.getMessage(), exception
        );
    }

    @ExceptionHandler(SavedMomentTargetNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleTargetNotFound(SavedMomentTargetNotFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "SAVED_MOMENT_TARGET_NOT_FOUND", "Video moment not found", exception
        );
    }

    @ExceptionHandler(SavedMomentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(SavedMomentNotFoundException exception) {
        return PublicApiErrorResponses.clientError(
                HttpStatus.NOT_FOUND, "SAVED_MOMENT_NOT_FOUND", "Saved moment not found", exception
        );
    }
}
