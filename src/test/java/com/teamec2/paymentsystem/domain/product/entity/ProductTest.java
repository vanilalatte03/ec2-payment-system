package com.teamec2.paymentsystem.domain.product.entity;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void 재고복구_수량이0이면_INVALID_RESTORE_STOCK_QUANTITY가발생한다() {
        // given
        Product product = 상품_생성(10, ProductStatus.ON_SALE);

        // when
        // then
        assertThatThrownBy(() -> product.restoreStock(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RESTORE_STOCK_QUANTITY);
    }

    @Test
    void 재고복구_품절상품의재고가생기면_판매중으로복구한다() {
        // given
        Product product = 상품_생성(0, ProductStatus.SOLD_OUT);

        // when
        product.restoreStock(1);

        // then
        assertThat(product.getStock()).isEqualTo(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 재고복구_판매중단상품이면_재고가생겨도_판매중으로바꾸지않는다() {
        // given
        Product product = 상품_생성(0, ProductStatus.DISCONTINUED);

        // when
        product.restoreStock(1);

        // then
        assertThat(product.getStock()).isEqualTo(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
    }

    private Product 상품_생성(int stock, ProductStatus status) {
        return new Product(
                "테스트 상품",
                1000,
                stock,
                "테스트 상품 설명",
                status,
                ProductCategory.ELECTRONIC
        );
    }
}
