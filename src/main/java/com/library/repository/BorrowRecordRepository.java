package com.library.repository;

import com.library.entity.BorrowRecord;
import com.library.entity.BorrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    List<BorrowRecord> findByUserIdOrderByBorrowDateDescIdDesc(Long userId);
    List<BorrowRecord> findAllByOrderByBorrowDateDescIdDesc();
    boolean existsByUserIdAndBookIdAndStatus(Long userId, Long bookId, BorrowStatus status);
    boolean existsByBookId(Long bookId);
    long countByStatus(BorrowStatus status);
    long countByStatusAndDueDateBefore(BorrowStatus status, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BorrowRecord r where r.id = :id")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);
}
