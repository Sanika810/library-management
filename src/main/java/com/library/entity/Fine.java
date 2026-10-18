package com.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One fine per borrow record (unique FK). 'amount' is the ORIGINAL assessed fine and never changes.
 * Outstanding = amount - SUM(adjustments) - SUM(SUCCESS payments); only 'status' is cached for filtering.
 */
@Entity
@Table(name = "fines", indexes = @Index(name = "idx_fine_status", columnList = "status"))
@Getter @Setter @NoArgsConstructor
public class Fine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "borrow_record_id", nullable = false, unique = true)
    private BorrowRecord borrowRecord;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int overdueDays;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FineStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
