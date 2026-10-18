package com.library.service;

import com.library.dto.PaymentRequest;
import com.library.entity.*;
import com.library.exception.BusinessException;
import com.library.payment.PaymentProcessor;
import com.library.payment.PaymentResult;
import com.library.repository.FineRepository;
import com.library.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final FineRepository fineRepo;
    private final PaymentRepository paymentRepo;
    private final FineService fineService;
    private final PaymentProcessor processor;

    /**
     * One transaction per payment attempt.
     * 1. Lock the fine row (SELECT ... FOR UPDATE) as the FIRST statement. Concurrent payments on the same fine
     *    queue here, and everything read afterwards (including MySQL's REPEATABLE READ snapshot) is current,
     *    so two payments can never both fit into the same outstanding balance.
     * 2. Verify ownership, amount, idempotency key and outstanding balance (all server-side).
     * 3. Save PENDING, ask the processor, set SUCCESS/FAILED, re-derive fine status.
     * Business-rule violations throw (rollback, nothing stored). A processor failure is stored as FAILED.
     */
    @Transactional
    public Payment pay(Long userId, Long fineId, PaymentRequest req) {
        Fine fine = fineRepo.findByIdForUpdate(fineId)
                .orElseThrow(() -> BusinessException.notFound("Fine not found"));
        if (!fine.getBorrowRecord().getUser().getId().equals(userId)) {
            throw BusinessException.notFound("Fine not found");
        }
        BigDecimal amount = validAmount(req.getAmount());
        if (req.getMethod() == null) {
            throw new BusinessException("Select a payment method");
        }
        String key = req.getIdempotencyKey();
        if (key == null || key.isBlank() || key.length() > 40) {
            throw new BusinessException("Invalid payment request");
        }

        // Same form submitted twice -> return the original attempt, never process again.
        Optional<Payment> duplicate = paymentRepo.findByIdempotencyKey(key);
        if (duplicate.isPresent()) {
            Payment existing = duplicate.get();
            if (!existing.getFine().getId().equals(fineId)) {
                throw new BusinessException("Invalid payment request");
            }
            return existing;
        }

        BigDecimal outstanding = fineService.outstanding(fine);
        if (outstanding.signum() <= 0) {
            throw new BusinessException("This fine has no outstanding balance");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException("Amount exceeds the outstanding balance of " + outstanding.setScale(2, RoundingMode.HALF_UP));
        }

        Payment payment = new Payment();
        payment.setFine(fine);
        payment.setAmount(amount);
        payment.setMethod(req.getMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(key);
        payment = paymentRepo.save(payment);

        PaymentResult result;
        try {
            result = processor.process(payment);
        } catch (RuntimeException e) {
            result = new PaymentResult(false, null);
        }
        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionReference(result.reference());
            payment.setPaymentDate(LocalDateTime.now());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        paymentRepo.save(payment);
        fineService.refreshStatus(fine);
        return payment;
    }

    private static BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Payment amount can have at most 2 decimal places");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public List<Payment> userPayments(Long userId) {
        return paymentRepo.findByFineBorrowRecordUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    /** A payment of another user is reported as 'not found'. */
    public Payment ownedPayment(Long userId, Long paymentId) {
        return paymentRepo.findByIdAndFineBorrowRecordUserId(paymentId, userId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found"));
    }

    public List<Payment> all(PaymentStatus status) {
        return status == null
                ? paymentRepo.findAllByOrderByCreatedAtDescIdDesc()
                : paymentRepo.findByStatusOrderByCreatedAtDescIdDesc(status);
    }
}
