package com.teamec2.paymentsystem.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Scheduler 기능을 활성화하는 설정입니다.
 * RefundScheduler의 @Scheduled 메서드가 동작하려면 @EnableScheduling이 필요합니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
