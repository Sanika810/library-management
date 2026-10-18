package com.library.repository;

import com.library.entity.Fine;
import com.library.entity.FineStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {
    Optional<Fine> findByBorrowRecordId(Long borrowRecordId);
    List<Fine> findByBorrowRecordUserIdOrderByCreatedAtDescIdDesc(Long userId);
    long countByStatus(FineStatus status);

    /** Row lock used by every financial update. Must be the FIRST statement of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fine f where f.id = :id")
    Optional<Fine> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select f from Fine f
            where f.status in :statuses
              and (:q = ''
                   or lower(f.borrowRecord.user.name) like lower(concat('%', :q, '%'))
                   or lower(f.borrowRecord.user.email) like lower(concat('%', :q, '%'))
                   or lower(f.borrowRecord.book.title) like lower(concat('%', :q, '%'))
                   or str(f.borrowRecord.id) = :q)
            order by f.createdAt desc, f.id desc
            """)
    List<Fine> search(@Param("statuses") Collection<FineStatus> statuses, @Param("q") String q);

    @Query("select sum(f.amount) from Fine f")
    BigDecimal sumAmount();

    @Query("select count(distinct f.borrowRecord.user.id) from Fine f where f.status in :statuses")
    long countUsersWithStatusIn(@Param("statuses") Collection<FineStatus> statuses);
}
