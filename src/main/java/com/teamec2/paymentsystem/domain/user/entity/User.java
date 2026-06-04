package com.teamec2.paymentsystem.domain.user.entity;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "point_balance", nullable = false)
    private long pointBalance;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private User(String email, String password, String name, String phone) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.pointBalance = 0L;
    }

    public static User create(String email, String password, String name, String phone) {
        return new User(email, password, name, phone);
    }

    // 증가 포인트 메서드
    public void increasePointBalance(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.POINT_INCREASE_AMOUNT_INVALID);
        }
        this.pointBalance += amount;
    }

    // 감소 포인트 메서드
    public void decreasePointBalance(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.POINT_DECREASE_AMOUNT_INVALID);
        }

        // 포인트 잔액은 음수가 되면 안 되므로, 차감 전 현재 잔액이 충분한지 검증합니다.
        if (this.pointBalance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }

        this.pointBalance -= amount;
    }
}
