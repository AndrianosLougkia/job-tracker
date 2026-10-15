package com.jobtracker.infrastructure.exception;

import com.jobtracker.analysis.domain.AnalysisInputException;
import com.jobtracker.auth.domain.InvalidCredentialsException;
import com.jobtracker.resume.domain.FileTooLargeException;
import com.jobtracker.resume.domain.InvalidFileException;
import com.jobtracker.resume.infrastructure.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(errorBody(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("Login failed: {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(errorBody(401, "Unauthorized", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
            .body(errorBody(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFile(
            InvalidFileException ex, HttpServletRequest request) {
        log.warn("Invalid file: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(
            FileTooLargeException ex, HttpServletRequest request) {
        log.warn("File too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(errorBody(413, "Payload Too Large", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload rejected by multipart size limit on {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(errorBody(413, "Payload Too Large", "File exceeds the 10 MB limit", request.getRequestURI()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(
            TooManyRequestsException ex, HttpServletRequest request) {
        log.warn("Limit reached on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        long retryAfterSeconds = Math.max(1, ex.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
            .body(errorBody(429, "Too Many Requests", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(
            StorageException ex, HttpServletRequest request) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorBody(503, "Service Unavailable", "Storage service is unavailable",
                request.getRequestURI()));
    }

    @ExceptionHandler(AnalysisInputException.class)
    public ResponseEntity<Map<String, Object>> handleAnalysisInput(
            AnalysisInputException ex, HttpServletRequest request) {
        log.warn("Analysis input rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(errorBody(422, "Unprocessable Entity", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Unreadable request body on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.badRequest()
            .body(errorBody(400, "Bad Request", "Malformed request body", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Invalid parameter '{}' on {} {}", ex.getName(), request.getMethod(), request.getRequestURI());
        return ResponseEntity.badRequest()
            .body(errorBody(400, "Bad Request", "Invalid value for parameter '" + ex.getName() + "'",
                request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .sorted().toList();
        log.warn("Validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), errors);
        Map<String, Object> body = errorBody(400, "Bad Request", "Validation failed", request.getRequestURI());
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            return handleFrameworkError(errorResponse, request);
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody(500, "Internal Server Error", "An unexpected error occurred",
                request.getRequestURI()));
    }

    /**
     * Spring MVC's own client errors (unknown route, unsupported method or media type,
     * missing parameter, ...) carry their status; keep it instead of reporting a 500.
     */
    private ResponseEntity<Map<String, Object>> handleFrameworkError(
            ErrorResponse ex, HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        String detail = ex instanceof NoResourceFoundException
            ? "No endpoint " + request.getMethod() + " " + request.getRequestURI()
            : ex.getBody().getDetail() != null ? ex.getBody().getDetail() : reason;
        if (status.is5xxServerError()) {
            log.error("Framework error on {} {}: {}", request.getMethod(), request.getRequestURI(), detail);
        } else {
            log.warn("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(), detail);
        }
        return ResponseEntity.status(status).headers(ex.getHeaders())
            .body(errorBody(status.value(), reason, detail, request.getRequestURI()));
    }

    // public — as per your debugged version
    public static Map<String, Object> errorBody(int status, String error, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        return body;
    }
}
