package com.teamec2.paymentsystem.infra.portone.webhook.repository;

import com.teamec2.paymentsystem.infra.portone.webhook.entity.PortoneWebhookEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PortoneWebhookEventRepositoryTest {

    @Autowired
    PortoneWebhookEventRepository webhookEventRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void WebhookId_존재여부를확인할수있다() {
        // given
        PortoneWebhookEvent event = PortoneWebhookEvent.received(
                "webhook-1",
                "Transaction.Paid",
                "pay_123",
                "{}"
        );
        webhookEventRepository.saveAndFlush(event);
        entityManager.clear();

        // when
        boolean exists = webhookEventRepository.existsByWebhookId("webhook-1");
        boolean notExists = webhookEventRepository.existsByWebhookId("webhook-unknown");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void WebhookId는_중복저장할수없다() {
        // given
        webhookEventRepository.saveAndFlush(PortoneWebhookEvent.received(
                "webhook-duplicate",
                "Transaction.Paid",
                "pay_123",
                "{}"
        ));

        // when
        // then
        assertThatThrownBy(() -> webhookEventRepository.saveAndFlush(PortoneWebhookEvent.received(
                "webhook-duplicate",
                "Transaction.Paid",
                "pay_456",
                "{}"
        )))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
