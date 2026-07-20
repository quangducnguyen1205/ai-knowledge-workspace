package com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.common.web.api.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.api.PublicApiErrorResponses;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class UnexpectedApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return PublicApiErrorResponses.unexpected(exception);
    }
}
