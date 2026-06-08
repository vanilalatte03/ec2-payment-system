package com.teamec2.paymentsystem.domain.order.repository;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderItemRepositoryTest {

    private static final Long FIRST_SOURCE_CART_ITEM_ID = 1L;
    private static final Long SECOND_SOURCE_CART_ITEM_ID = 2L;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 주문ID로_주문상품과상품을함께조회한다() {
        // given
        User user = 회원_저장("order-item@example.com");
        Product firstProduct = 상품_저장("후드 집업", 55000);
        Product secondProduct = 상품_저장("볼캡", 24000);
        Order order = 주문_저장(user, "ORDER-ITEM-001", 158000L, 0L);
        orderItemRepository.save(new OrderItem(order, firstProduct, FIRST_SOURCE_CART_ITEM_ID, 2));
        orderItemRepository.save(new OrderItem(order, secondProduct, SECOND_SOURCE_CART_ITEM_ID, 3));

        entityManager.flush();
        entityManager.clear();

        // when
        List<OrderItem> orderItems = orderItemRepository.findWithProductByOrderId(order.getId());
        List<OrderItem> emptyOrderItems = orderItemRepository.findWithProductByOrderId(-1L);

        // then
        assertThat(orderItems).hasSize(2);
        assertThat(orderItems)
                .extracting(OrderItem::getProductName)
                .containsExactlyInAnyOrder("후드 집업", "볼캡");
        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(orderItems.get(0), "product")).isTrue();
        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(orderItems.get(1), "product")).isTrue();
        assertThat(emptyOrderItems).isEmpty();
    }

    private User 회원_저장(String email) {
        User user = User.create(email, "Password123!", "홍길동", "010-1234-5678");
        entityManager.persist(user);
        return user;
    }

    private Product 상품_저장(String name, int price) {
        Product product = new Product(
                name,
                price,
                10,
                "테스트 상품",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        );
        entityManager.persist(product);
        return product;
    }

    private Order 주문_저장(User user, String orderNumber, Long totalAmount, Long usedPointAmount) {
        Order order = Order.create(user, orderNumber, totalAmount, usedPointAmount);
        entityManager.persist(order);
        return order;
    }
}
