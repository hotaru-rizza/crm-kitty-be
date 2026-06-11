package com.inkflow.crm.common.exception;

import com.inkflow.crm.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiException_returnsErrorCodeAndStatus() {
        ApiException ex = new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");

        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(ex);

        assertStatus(HttpStatus.UNAUTHORIZED, response);
        assertErrorCode("UNAUTHORIZED", response);
    }

    @Test
    void handleIllegalArgument_returnsValidationError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Invalid key")
        );

        assertStatus(HttpStatus.BAD_REQUEST, response);
        assertErrorCode("VALIDATION_ERROR", response);
    }

    @Test
    void handleValidationException_mapsFieldErrors() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "phone", "Invalid phone"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertStatus(HttpStatus.BAD_REQUEST, response);
        assertErrorCode("VALIDATION_ERROR", response);
    }

    private void assertStatus(HttpStatus expected, ResponseEntity<ApiResponse<Void>> response) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, response.getStatusCode());
    }

    private void assertErrorCode(String code, ResponseEntity<ApiResponse<Void>> response) {
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody().getError());
        org.junit.jupiter.api.Assertions.assertEquals(code, response.getBody().getError().getError().getCode());
    }
}
