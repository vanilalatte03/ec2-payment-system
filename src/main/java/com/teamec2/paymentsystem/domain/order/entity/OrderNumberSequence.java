package com.teamec2.paymentsystem.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "order_number_sequences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderNumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_date", nullable = false, unique = true)
    private LocalDate orderDate;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    private OrderNumberSequence(LocalDate orderDate) {
        if (orderDate == null) {
            throw new IllegalArgumentException("orderDate must not be null");
        }

        this.orderDate = orderDate;
        this.lastNumber = 0;
    }

    public static OrderNumberSequence create(LocalDate orderDate) {
        return new OrderNumberSequence(orderDate);
    }

    public int increaseAndGet() {
        this.lastNumber += 1;
        return lastNumber;
    }
}
