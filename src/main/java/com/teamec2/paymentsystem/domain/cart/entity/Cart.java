package com.teamec2.paymentsystem.domain.cart.entity;

import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 낙관락
     *
     * 처음 읽었을 때의 장바구니 버전과 저장하려는 순간의 장바구니 버전이 같은가?
     * 를 확인하기 위한 변수입니다.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    public Cart(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        this.user = user;
    }
}
