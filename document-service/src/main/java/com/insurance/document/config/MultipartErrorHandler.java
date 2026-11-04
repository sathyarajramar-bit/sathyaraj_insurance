package com.insurance.document.config;

import com.insurance.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * A file over {@code spring.servlet.multipart.max-file-size} is rejected by the multipart resolver before any
 * controller runs, so a controller-local handler never sees it and common-lib's catch-all turned it into a 500.
 * This advice is ordered ahead of {@code GlobalExceptionHandler} and answers with the platform error body and 413.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MultipartErrorHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "PAYLOAD_TOO_LARGE",
                        "The uploaded file exceeds the maximum allowed size", request.getRequestURI()));
    }
}
