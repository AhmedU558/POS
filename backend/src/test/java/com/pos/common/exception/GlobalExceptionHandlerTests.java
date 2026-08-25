package com.pos.common.exception;

import com.pos.common.response.ApiErrorResponse;
import com.pos.common.response.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the centralised error contract (REST API Specification sections 5.2 and 28).
 *
 * <p>Runs without a Spring context so the mapping rules are asserted directly rather than through
 * an endpoint that does not exist yet.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionsKeepTheirDocumentedCodeAndStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleApiException(
                        new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Only 2 units remain."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(response).error().code()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(body(response).error().message()).isEqualTo("Only 2 units remain.");
        assertThat(body(response).meta().requestId()).isNotBlank();
        assertThat(body(response).meta().timestamp()).isNotNull();
    }

    @Test
    void constraintViolationsBecomeFieldLevelValidationDetails() {
        ConstraintViolationException violation =
                new ConstraintViolationException(violationsFor(new SaleLine("", -1)));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(violation);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body(response).error().details())
                .isNotNull()
                .hasSize(2)
                .allSatisfy(detail -> assertThat(detail.message()).isNotBlank());
        assertThat(body(response).error().details())
                .extracting(d -> d.field())
                .containsExactlyInAnyOrder("productSku", "quantity");
    }

    @Test
    void accessDeniedMapsToTheForbiddenCode() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("no"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(response).error().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void unexpectedFailuresDoNotLeakInternalDetailToTheClient() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(
                        new IllegalStateException("jdbc:postgresql://db/pos_db credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(response).error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body(response).error().message())
                .isEqualTo(ErrorCode.INTERNAL_ERROR.defaultMessage())
                .doesNotContain("jdbc");
        assertThat(body(response).error().details()).isNull();
    }

    @Test
    void everyDocumentedErrorCodeMapsIntoTheApprovedStatusSet() {
        // REST API Specification section 3 lists the permitted response statuses.
        Set<HttpStatus> permitted =
                Set.of(
                        HttpStatus.BAD_REQUEST,
                        HttpStatus.UNAUTHORIZED,
                        HttpStatus.FORBIDDEN,
                        HttpStatus.NOT_FOUND,
                        HttpStatus.CONFLICT,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        HttpStatus.TOO_MANY_REQUESTS,
                        HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(ErrorCode.values())
                .allSatisfy(code -> assertThat(permitted).contains(code.status()));
    }

    @Test
    void theCatalogueMatchesTheSpecifiedErrorCodes() {
        assertThat(ErrorCode.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrderElementsOf(
                        List.of(
                                "AUTHENTICATION_REQUIRED",
                                "ACCESS_DENIED",
                                "VALIDATION_ERROR",
                                "RESOURCE_NOT_FOUND",
                                "RESOURCE_INACTIVE",
                                "BUSINESS_RULE_VIOLATION",
                                "INSUFFICIENT_STOCK",
                                "REGISTER_SESSION_REQUIRED",
                                "REGISTER_ALREADY_OPEN",
                                "DUPLICATE_REQUEST",
                                "PAYMENT_FAILED",
                                "REFUND_NOT_ALLOWED",
                                "CONFLICT",
                                "RATE_LIMITED",
                                "INTERNAL_ERROR"));
    }

    private static ApiErrorResponse body(ResponseEntity<ApiErrorResponse> response) {
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private static <T> Set<ConstraintViolation<T>> violationsFor(T bean) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(bean);
        }
    }

    /** Stand-in for a request DTO; mirrors the sale-item rules in API spec section 26. */
    private record SaleLine(@NotBlank String productSku, @Positive int quantity) {}
}
