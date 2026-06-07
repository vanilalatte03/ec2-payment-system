package com.teamec2.paymentsystem.domain.product.entity;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void 상품생성_가격이음수면_INVALID_PRICE가발생한다() {
        // given

        // when
        // then
        assertThatThrownBy(() -> new Product(
                "테스트 상품",
                -1,
                10,
                "테스트 상품 설명",
                ProductStatus.ON_SALE,
                ProductCategory.ELECTRONIC
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PRICE);
    }

    @Test
    void 상품생성_재고가음수면_INVALID_STOCK이발생한다() {
        // given

        // when
        // then
        assertThatThrownBy(() -> new Product(
                "테스트 상품",
                1000,
                -1,
                "테스트 상품 설명",
                ProductStatus.ON_SALE,
                ProductCategory.ELECTRONIC
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STOCK);
    }

    @Test
    void 재고차감_수량이0이면_INVALID_ORDER_QUANTITY가발생한다() {
        // given
        Product product = 상품_생성(10, ProductStatus.ON_SALE);

        // when
        // then
        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
    }

    @Test
    void 재고차감_판매중이아니면_PRODUCT_NOT_ON_SALE이발생한다() {
        // given
        Product product = 상품_생성(10, ProductStatus.DISCONTINUED);

        // when
        // then
        assertThatThrownBy(() -> product.decreaseStock(1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    void 재고차감_재고보다많은수량이면_PRODUCT_OUT_OF_STOCK이발생하고_재고는변하지않는다() {
        // given
        Product product = 상품_생성(2, ProductStatus.ON_SALE);

        // when
        // then
        assertThatThrownBy(() -> product.decreaseStock(3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
        assertThat(product.getStock()).isEqualTo(2);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 재고차감_남은재고가0이되면_품절상태로변경한다() {
        // given
        Product product = 상품_생성(2, ProductStatus.ON_SALE);

        // when
        product.decreaseStock(2);

        // then
        assertThat(product.getStock()).isZero();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void 재고차감_이미재고가0이면_PRODUCT_OUT_OF_STOCK이발생하고_품절상태로변경한다() {
        // given
        Product product = 상품_생성(0, ProductStatus.ON_SALE);

        // when
        // then
        assertThatThrownBy(() -> product.decreaseStock(1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
        assertThat(product.getStock()).isZero();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

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
