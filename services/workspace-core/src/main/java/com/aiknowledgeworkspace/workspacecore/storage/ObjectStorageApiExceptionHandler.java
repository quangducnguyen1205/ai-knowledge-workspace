package com.aiknowledgeworkspace.workspacecore.storage;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ObjectStorageApiExceptionHandler {

    @ExceptionHandler(ObjectStorageException.class)
    ResponseEntity<ApiErrorResponse> handleObjectStorage(ObjectStorageException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("OBJECT_STORAGE_ERROR", exception.getMessage()));
    }
}
