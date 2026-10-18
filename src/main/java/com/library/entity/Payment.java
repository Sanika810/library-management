package com.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A payment ATTEMPT against a fine. Only SUCCESS rows count as paid. Rows are never deleted. */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payment_fine_status", columnList = "fine_id, status"))
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fine_id", nullable = false)
    private Fine fine;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Set only for SUCCESS payments; unique. */
    @Column(unique = true, length = 40)
    private String transactionReference;

    /** Server-generated token per pay-form; unique, so the same submission can never be processed twice. */
    @Column(nullable = false, unique = true, length = 40)
    private String idempotencyKey;

    private LocalDateTime paymentDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
