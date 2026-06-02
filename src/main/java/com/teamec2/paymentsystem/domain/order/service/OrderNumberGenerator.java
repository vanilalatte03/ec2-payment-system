package com.teamec2.paymentsystem.domain.order.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 주문번호는 서버가 자동으로 만드는 고유값입니다.
    // DB 조회 후 저장하는 방식은 동시 요청에서 원자적이지 않으므로, 충돌 가능성이 매우 낮은 전체 UUID를 사용합니다.
    public String generate() {
        String date = LocalDate.now().format(DATE_FORMATTER);
        String randomValue = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();

        return "ORD-" + date + "-" + randomValue;
    }
}
