package com.pos.common.exception;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiErrorDetail;
import com.pos.common.response.ApiErrorResponse;
import com.pos.common.response.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Centralised exception handling.
 *
 * <p>Required by Implementation Plan section 6 ("Use centralized exception handling with
 * {@code @RestControllerAdvice}") and REST API Specification section 35. Every handler renders the
 * standard error envelope from REST API Specification section 5.2 and a documented error code from
 * section 28.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return build(ex.errorCode(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException ex) {
        List<ApiErrorDetail> details =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new ApiErrorDetail(fe.getField(), fe.getDefaultMessage()))
                        .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<ApiErrorDetail> details =
                ex.getConstraintViolations().stream()
                        .map(this::toDetail)
                        .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception ex) {
        log.debug("Rejected malformed request: {}", ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "The request could not be read.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return build(
                ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(), List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(
                ErrorCode.AUTHENTICATION_REQUIRED,
                ErrorCode.AUTHENTICATION_REQUIRED.defaultMessage(),
                List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), List.of());
    }

    /**
     * Last-resort handler. The cause is logged with the request correlation id; the client receives
     * only the generic message, per REST API Specification section 29.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of());
    }

    private ApiErrorDetail toDetail(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString();
        return new ApiErrorDetail(path, violation.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> build(
            ErrorCode code, String message, List<ApiErrorDetail> details) {
        List<ApiErrorDetail> payload = (details == null || details.isEmpty()) ? null : details;
        return ResponseEntity.status(code.status())
                .body(
                        ApiErrorResponse.of(
                                code, message, payload, RequestCorrelation.currentId()));
    }
}
