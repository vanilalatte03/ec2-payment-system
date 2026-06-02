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

    // 주문 생성 시점에 재고를 먼저 차감합니다.
    // 재고가 부족하면 예외가 발생하고, 주문 생성 트랜잭션이 함께 롤백됩니다.
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        if (this.stock < quantity) {
            throw new BusinessException(ErrorCode.ORDER_STOCK_SHORTAGE);
        }

        this.stock -= quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        this.stock += quantity;
    }
}
