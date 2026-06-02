package com.teamec2.paymentsystem.domain.order.repository;

import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
