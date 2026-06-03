package com.teamec2.paymentsystem.domain.payment.repository;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PaymentRepositoryTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void PortOne결제ID로_결제를조회할수있다() {
        // given
        Payment payment = 결제_저장("ORDER-001");
        String portonePaymentId = payment.getPortonePaymentId();

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Payment> foundPayment = paymentRepository.findByPortonePaymentId(portonePaymentId);

        // then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getPortonePaymentId()).isEqualTo(portonePaymentId);
    }

    @Test
    void PortOne결제ID_존재여부를확인할수있다() {
        // given
        Payment payment = 결제_저장("ORDER-001");

        entityManager.flush();
        entityManager.clear();

        // when
        boolean exists = paymentRepository.existsByPortonePaymentId(payment.getPortonePaymentId());
        boolean notExists = paymentRepository.existsByPortonePaymentId("pay_unknown");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void 하나의주문에는_하나의결제만저장할수있다() {
        // given
        Order order = 주문_저장("ORDER-001");
        paymentRepository.saveAndFlush(Payment.createPending(order, 1000L, 200L, 800L, 8L));

        // when
        // then
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(Payment.createPending(order, 1000L, 200L, 800L, 8L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 주문ID로_결제를조회할수있다() {
        // given
        Payment payment = 결제_저장("ORDER-001");
        Long orderId = payment.getOrder().getId();

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Payment> foundPayment = paymentRepository.findByOrderIdForUpdate(orderId);

        // then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getId()).isEqualTo(payment.getId());
        assertThat(foundPayment.get().getOrder().getId()).isEqualTo(orderId);
    }

    private Payment 결제_저장(String orderNumber) {
        Order order = 주문_저장(orderNumber);
        return paymentRepository.save(Payment.createPending(order, 1000L, 200L, 800L, 8L));
    }

    private Order 주문_저장(String orderNumber) {
        User user = User.create(
                orderNumber.toLowerCase() + "@example.com",
                "password",
                "테스트유저",
                "010-1234-5678"
        );
        entityManager.persist(user);

        Order order = Order.create(user, orderNumber, 1000L, 200L);
        entityManager.persist(order);

        return order;
    }
}
