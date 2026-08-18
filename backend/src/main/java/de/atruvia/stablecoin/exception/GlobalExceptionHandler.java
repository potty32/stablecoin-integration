package de.atruvia.stablecoin.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import de.atruvia.stablecoin.exception.WebhookSignatureException;
import de.atruvia.stablecoin.exception.PaymentSystemFrozenException;
import de.atruvia.stablecoin.exception.SlippageExceededException;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SlippageExceededException.class)
    public ResponseEntity<ErrorResponse> handleSlippage(SlippageExceededException ex) {
        log.warn("[SLIPPAGE] {} BPS > {} BPS Limit", ex.getActualBps(), ex.getLimitBps());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("BIZ_005", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(PaymentSystemFrozenException.class)
    public ResponseEntity<ErrorResponse> handlePaymentSystemFrozen(PaymentSystemFrozenException ex) {
        log.error("[KILL-SWITCH] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("SYSTEM_003", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<ErrorResponse> handleWebhookSignature(WebhookSignatureException ex) {
        log.warn("[WEBHOOK-SEC] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("AUTH_002", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[ACCESS DENIED] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("AUTH_001", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(ComplianceBlockException.class)
    public ResponseEntity<ErrorResponse> handleComplianceBlock(ComplianceBlockException ex, HttpServletRequest req) {
        log.error("[COMPLIANCE BLOCK] address={} msg={}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("COMPLIANCE_001", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(TaurusLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTaurusLimit(TaurusLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("TAURUS_001", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotency(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("IDEM_001", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VAL_001", message, getTraceId()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        log.warn("[NOT FOUND] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND_001", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("[BAD REQUEST] {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BIZ_001", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[BAD REQUEST] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VAL_002", ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("[CONFLICT] {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT_001", "Resource already exists", getTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("SYS_001", "Internal server error", getTraceId()));
    }

    private String getTraceId() {
        return Optional.ofNullable(MDC.get("traceId")).orElse("n/a");
    }

    public record ErrorResponse(String errorCode, String message, String traceId) {}
}
