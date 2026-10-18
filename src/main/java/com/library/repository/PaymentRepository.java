package com.library.repository;

import com.library.entity.Payment;
import com.library.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByIdAndFineBorrowRecordUserId(Long id, Long userId);
    List<Payment> findByFineBorrowRecordUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<Payment> findAllByOrderByCreatedAtDescIdDesc();
    List<Payment> findByStatusOrderByCreatedAtDescIdDesc(PaymentStatus status);

    @Query("select sum(p.amount) from Payment p where p.fine.id = :fineId and p.status = :status")
    BigDecimal sumByFineAndStatus(@Param("fineId") Long fineId, @Param("status") PaymentStatus status);

    @Query("select sum(p.amount) from Payment p where p.status = :status")
    BigDecimal sumByStatus(@Param("status") PaymentStatus status);
}
