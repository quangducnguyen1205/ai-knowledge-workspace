package com.aiknowledgeworkspace.workspacecore.storage;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import com.aiknowledgeworkspace.workspacecore.common.web.PublicApiErrorResponses;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ObjectStorageApiExceptionHandler {

    @ExceptionHandler(ObjectStorageException.class)
    ResponseEntity<ApiErrorResponse> handleObjectStorage(ObjectStorageException exception) {
        return PublicApiErrorResponses.serviceUnavailable("STORAGE_SERVICE_UNAVAILABLE", exception);
    }
}
