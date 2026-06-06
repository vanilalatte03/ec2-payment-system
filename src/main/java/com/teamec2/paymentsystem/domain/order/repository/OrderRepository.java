package com.teamec2.paymentsystem.domain.order.repository;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
