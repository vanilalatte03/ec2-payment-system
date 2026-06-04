package com.teamec2.paymentsystem.domain.product.entity;

import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "int UNSIGNED")
    private int price;

    @Column(nullable = false, columnDefinition = "int UNSIGNED DEFAULT 0")
    private int stock = 0;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductCategory category;

    public Product(String name, int price, int stock, String description, ProductStatus status, ProductCategory category) {
        if (price < 0) {
            throw new BusinessException(ErrorCode.INVALID_PRICE);
        }

        if (stock < 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK);
        }

        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.status = status;
        this.category = category;
    }

    public void decreaseStock(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        if (this.status == ProductStatus.DISCONTINUED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }

        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;

            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (this.stock < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        this.stock -= quantity;

        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    // 주문 취소 시 수량 변경
    public void restoreStock(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_RESTORE_STOCK_QUANTITY);
        }

        this.stock += quantity;

        // 품절 상품이 취소되었다면 판매중으로 복구
        if (this.status == ProductStatus.SOLD_OUT && this.stock > 0) {
            this.status = ProductStatus.ON_SALE;
        }
    }
}
