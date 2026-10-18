package com.library.service;

import com.library.dto.FeeStats;
import com.library.dto.FineView;
import com.library.entity.*;
import com.library.exception.BusinessException;
import com.library.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Fine calculation and derived balances. Payment processing lives in PaymentService. */
@Service
@RequiredArgsConstructor
public class FineService {
    public static final BigDecimal DEFAULT_RATE = new BigDecimal("5.00");

    private final FineRepository fineRepo;
    private final FineSettingRepository settingRepo;
    private final PaymentRepository paymentRepo;
    private final FineAdjustmentRepository adjustmentRepo;
    private final BorrowRecordRepository recordRepo;

    /**
     * Whole days between due date and return date (LocalDate, so no time-of-day or DST issues).
     * Returned on/before the due date -> 0. Due 15 Oct, returned 19 Oct -> 4.
     */
    public static int overdueDays(LocalDate dueDate, LocalDate returnDate) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, returnDate));
    }

    public static BigDecimal fineAmount(int overdueDays, BigDecimal ratePerDay) {
        return ratePerDay.multiply(BigDecimal.valueOf(overdueDays)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------- fine rate ----------

    public BigDecimal currentRate() {
        return settingRepo.findAll().stream().findFirst()
                .map(FineSetting::getRatePerDay).orElse(DEFAULT_RATE);
    }

    @Transactional
    public void updateRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0 || rate.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Fine rate must be a positive amount with at most 2 decimal places");
        }
        FineSetting s = settingRepo.findAll().stream().findFirst().orElseGet(FineSetting::new);
        s.setRatePerDay(rate.setScale(2, RoundingMode.HALF_UP));
        settingRepo.save(s);
    }

    // ---------- creation (called from BorrowService.returnBook, same transaction) ----------

    @Transactional
    public Optional<Fine> createFineIfOverdue(BorrowRecord record) {
        if (record.getReturnDate() == null) {
            throw new IllegalStateException("Borrow record has no return date");
        }
        int days = overdueDays(record.getDueDate(), record.getReturnDate());
        if (days == 0) {
            return Optional.empty();
        }
        Optional<Fine> existing = fineRepo.findByBorrowRecordId(record.getId());
        if (existing.isPresent()) {
            return existing;
        }
        BigDecimal rate = currentRate();
        Fine fine = new Fine();
        fine.setBorrowRecord(record);
        fine.setOverdueDays(days);
        fine.setRatePerDay(rate);
        fine.setAmount(fineAmount(days, rate));
        fine.setStatus(FineStatus.UNPAID);
        return Optional.of(fineRepo.save(fine));
    }

    // ---------- derived balances ----------

    public BigDecimal paid(Long fineId) {
        return nz(paymentRepo.sumByFineAndStatus(fineId, PaymentStatus.SUCCESS));
    }

    public BigDecimal adjusted(Long fineId) {
        return nz(adjustmentRepo.sumByFine(fineId));
    }

    /** original - waived - successful payments. FAILED and PENDING payments never count. */
    public BigDecimal outstanding(Fine fine) {
        return fine.getAmount().subtract(adjusted(fine.getId())).subtract(paid(fine.getId()));
    }

    /** Re-derives the cached status. Call inside a transaction that already holds the fine's row lock. */
    @Transactional
    public void refreshStatus(Fine fine) {
        BigDecimal paid = paid(fine.getId());
        BigDecimal outstanding = outstanding(fine);
        FineStatus status;
        if (outstanding.signum() <= 0) {
            status = paid.signum() > 0 ? FineStatus.PAID : FineStatus.WAIVED;
        } else {
            status = paid.signum() > 0 ? FineStatus.PARTIALLY_PAID : FineStatus.UNPAID;
        }
        fine.setStatus(status);
        fineRepo.save(fine);
    }

    // ---------- views ----------

    public FineView view(Fine fine) {
        BigDecimal adjusted = adjusted(fine.getId());
        BigDecimal paid = paid(fine.getId());
        return new FineView(fine, adjusted, paid, fine.getAmount().subtract(adjusted).subtract(paid));
    }

    public FineView get(Long fineId) {
        return view(fineRepo.findById(fineId).orElseThrow(() -> BusinessException.notFound("Fine not found")));
    }

    /** Ownership check: a fine of another user is reported as 'not found'. */
    public FineView ownedView(Long userId, Long fineId) {
        Fine fine = fineRepo.findById(fineId)
                .filter(f -> f.getBorrowRecord().getUser().getId().equals(userId))
                .orElseThrow(() -> BusinessException.notFound("Fine not found"));
        return view(fine);
    }

    public List<FineView> userFines(Long userId) {
        return fineRepo.findByBorrowRecordUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(this::view).toList();
    }

    public List<FineView> search(String q, FineStatus status) {
        List<FineStatus> statuses = status == null ? List.of(FineStatus.values()) : List.of(status);
        return fineRepo.search(statuses, q == null ? "" : q.trim()).stream().map(this::view).toList();
    }

    public Optional<Fine> forRecord(Long recordId) {
        return fineRepo.findByBorrowRecordId(recordId);
    }

    // ---------- dashboard (all figures come from database aggregate queries) ----------

    public FeeStats stats() {
        BigDecimal assessed = nz(fineRepo.sumAmount());
        BigDecimal adjusted = nz(adjustmentRepo.sumAll());
        BigDecimal collected = nz(paymentRepo.sumByStatus(PaymentStatus.SUCCESS));
        return new FeeStats(
                assessed, adjusted, collected,
                assessed.subtract(adjusted).subtract(collected),
                fineRepo.countUsersWithStatusIn(List.of(FineStatus.UNPAID, FineStatus.PARTIALLY_PAID)),
                fineRepo.countByStatus(FineStatus.UNPAID),
                fineRepo.countByStatus(FineStatus.PARTIALLY_PAID),
                recordRepo.countByStatusAndDueDateBefore(BorrowStatus.BORROWED, LocalDate.now()));
    }
}
