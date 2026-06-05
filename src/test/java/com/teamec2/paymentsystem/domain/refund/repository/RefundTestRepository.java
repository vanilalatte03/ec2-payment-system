package com.teamec2.paymentsystem.domain.refund.repository;

import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 테스트에서 환불성 포인트 원장에 필요한 refundId를 만들기 위한 테스트 전용 Repository입니다.
 * 운영 코드에는 아직 RefundRepository가 없으므로 테스트 소스에만 둡니다.
 */
public interface RefundTestRepository extends JpaRepository<Refund, Long> {
}
