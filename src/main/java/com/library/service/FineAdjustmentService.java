package com.library.service;

import com.library.dto.AdjustmentRequest;
import com.library.entity.*;
import com.library.exception.BusinessException;
import com.library.repository.FineAdjustmentRepository;
import com.library.repository.FineRepository;
import com.library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FineAdjustmentService {
    private final FineRepository fineRepo;
    private final FineAdjustmentRepository adjustmentRepo;
    private final UserRepository userRepo;
    private final FineService fineService;

    /**
     * Rules: librarian only (checked here too, not just in the URL rules); reason mandatory; the amount is a
     * positive reduction no larger than the outstanding balance; every adjustment is a permanent audit row.
     * The fine is locked first (same reasoning as PaymentService.pay).
     */
    @Transactional
    public FineAdjustment adjust(Long fineId, Long librarianId, AdjustmentRequest req) {
        Fine fine = fineRepo.findByIdForUpdate(fineId)
                .orElseThrow(() -> BusinessException.notFound("Fine not found"));
        User librarian = userRepo.findById(librarianId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
        if (librarian.getRole() != Role.LIBRARIAN) {
            throw new AccessDeniedException("Only a librarian can adjust fines");
        }
        BigDecimal amount = req.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Adjustment amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Adjustment amount can have at most 2 decimal places");
        }
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (reason.isEmpty()) {
            throw new BusinessException("A reason is required for every adjustment");
        }
        if (reason.length() > 255) {
            throw new BusinessException("Reason is too long (max 255 characters)");
        }
        BigDecimal outstanding = fineService.outstanding(fine);
        if (outstanding.signum() <= 0) {
            throw new BusinessException("This fine has no outstanding balance to adjust");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException("Adjustment exceeds the outstanding balance of " + outstanding.setScale(2, RoundingMode.HALF_UP));
        }
        FineAdjustment adj = new FineAdjustment();
        adj.setFine(fine);
        adj.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
        adj.setReason(reason);
        adj.setLibrarian(librarian);
        adj = adjustmentRepo.save(adj);
        fineService.refreshStatus(fine);
        return adj;
    }

    public List<FineAdjustment> history(Long fineId) {
        return adjustmentRepo.findByFineIdOrderByCreatedAtDescIdDesc(fineId);
    }
}
