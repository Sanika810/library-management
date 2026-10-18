package com.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Audit row for a waiver/reduction. 'amount' is the positive amount removed from the fine. Never deleted. */
@Entity
@Table(name = "fine_adjustments", indexes = @Index(name = "idx_adjustment_fine", columnList = "fine_id"))
@Getter @Setter @NoArgsConstructor
public class FineAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fine_id", nullable = false)
    private Fine fine;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String reason;

    @ManyToOne(optional = false)
    @JoinColumn(name = "librarian_id", nullable = false)
    private User librarian;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
