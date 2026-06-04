package com.teamec2.paymentsystem.infra.portone.webhook.repository;

import com.teamec2.paymentsystem.infra.portone.webhook.entity.PortoneWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortoneWebhookEventRepository extends JpaRepository<PortoneWebhookEvent, Long> {

    boolean existsByWebhookId(String webhookId);
}
