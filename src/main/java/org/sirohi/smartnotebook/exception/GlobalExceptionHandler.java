package org.sirohi.smartnotebook.exception;

import org.sirohi.smartnotebook.gateway.ModelGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateDocumentException e) {
        log.warn("Duplicate document: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_DOCUMENT", e.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ModelGatewayException.class)
    public ResponseEntity<ErrorResponse> handleModelError(ModelGatewayException e) {
        log.error("Model gateway error", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("MODEL_UNAVAILABLE",
                        "AI model is not available. Please try again later."));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<QuotaExceededResponse> handleQuotaExceeded(QuotaExceededException e) {
        log.warn("Quota exceeded for {}: {}", e.getProviderName(), e.getLimitType());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Reset", e.getResetAt() != null ? e.getResetAt().toString() : "")
                .header("X-RateLimit-Limit-Type", e.getLimitType())
                .body(new QuotaExceededResponse(
                        "QUOTA_EXCEEDED",
                        e.getMessage(),
                        e.getProviderName(),
                        e.getLimitType(),
                        e.getResetAt()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred."));
    }

    public record ErrorResponse(String code, String message) {
    }

    public record QuotaExceededResponse(
            String code,
            String message,
            String provider,
            String limitType,
            java.time.OffsetDateTime resetAt
    ) {
    }
}
