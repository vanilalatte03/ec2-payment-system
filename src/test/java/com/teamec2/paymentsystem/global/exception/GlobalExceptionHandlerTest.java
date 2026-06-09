package com.teamec2.paymentsystem.global.exception;

import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void 타입불일치_Enum이면_INVALID_ENUM_VALUE를반환한다() {
        // given
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "INVALID",
                ProductCategory.class,
                "category",
                null,
                new IllegalArgumentException()
        );

        // when
        ResponseEntity<ApiResponse<Void>> response =
                globalExceptionHandler.handleMethodArgumentTypeMismatchException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_ENUM_VALUE.name());
    }

    @Test
    void 타입불일치_Enum이아니면_VALIDATION_FAILED를반환한다() {
        // given
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc",
                Integer.class,
                "page",
                null,
                new NumberFormatException()
        );

        // when
        ResponseEntity<ApiResponse<Void>> response =
                globalExceptionHandler.handleMethodArgumentTypeMismatchException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
    }
}
