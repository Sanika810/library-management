package com.library.repository;

import com.library.entity.FineAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface FineAdjustmentRepository extends JpaRepository<FineAdjustment, Long> {
    List<FineAdjustment> findByFineIdOrderByCreatedAtDescIdDesc(Long fineId);

    @Query("select sum(a.amount) from FineAdjustment a where a.fine.id = :fineId")
    BigDecimal sumByFine(@Param("fineId") Long fineId);

    @Query("select sum(a.amount) from FineAdjustment a")
    BigDecimal sumAll();
}
