package com.teamec2.paymentsystem.domain.refund.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "refunds")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Refund {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
