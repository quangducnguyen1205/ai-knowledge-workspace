package com.aiknowledgeworkspace.workspacecore.common.web;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class PublicApiErrorResponses {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String INTERNAL_ERROR_MESSAGE = "Đã xảy ra lỗi. Vui lòng thử lại sau.";
    public static final String SERVICE_UNAVAILABLE_MESSAGE =
            "Dịch vụ tạm thời chưa sẵn sàng. Vui lòng thử lại sau.";

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicApiErrorResponses.class);

    private PublicApiErrorResponses() {
    }

    public static ResponseEntity<ApiErrorResponse> clientError(
            HttpStatus status,
            String code,
            String safeMessage,
            Throwable exception
    ) {
        return response(status, code, safeMessage, exception);
    }

    public static ResponseEntity<ApiErrorResponse> serviceUnavailable(String code, Throwable exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, code, SERVICE_UNAVAILABLE_MESSAGE, exception);
    }

    public static ResponseEntity<ApiErrorResponse> unexpected(Throwable exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", INTERNAL_ERROR_MESSAGE, exception);
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String safeMessage,
            Throwable exception
    ) {
        String correlationId = UUID.randomUUID().toString();
        if (status.is5xxServerError()) {
            LOGGER.error("Public API request failed correlationId={} code={}", correlationId, code, exception);
        } else {
            LOGGER.info(
                    "Public API request rejected correlationId={} code={} exceptionType={}",
                    correlationId,
                    code,
                    exception.getClass().getSimpleName()
            );
        }
        return ResponseEntity.status(status)
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(new ApiErrorResponse(code, safeMessage));
    }
}
