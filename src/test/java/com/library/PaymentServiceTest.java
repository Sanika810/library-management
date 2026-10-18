package com.library;

import com.library.dto.AdjustmentRequest;
import com.library.dto.FeeStats;
import com.library.dto.PaymentRequest;
import com.library.entity.*;
import com.library.exception.BusinessException;
import com.library.payment.PaymentResult;
import com.library.service.FineAdjustmentService;
import com.library.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest extends AbstractIntegrationTest {
    @Autowired PaymentService paymentService;
    @Autowired FineAdjustmentService adjustmentService;

    private PaymentRequest req(String amount) {
        return req(amount, UUID.randomUUID().toString());
    }

    private PaymentRequest req(String amount, String key) {
        PaymentRequest r = new PaymentRequest();
        r.setAmount(amount == null ? null : bd(amount));
        r.setMethod(PaymentMethod.UPI);
        r.setIdempotencyKey(key);
        return r;
    }

    private Fine reload(Fine f) {
        return fineRepo.findById(f.getId()).orElseThrow();
    }

    @Test
    void fullPaymentMarksFinePaid() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        Payment p = paymentService.pay(u.getId(), fine.getId(), req("20.00"));
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertNotNull(p.getTransactionReference());
        assertEquals(FineStatus.PAID, reload(fine).getStatus());
        assertEquals(0, fineService.outstanding(reload(fine)).signum());
        assertEquals(0, bd("20.00").compareTo(reload(fine).getAmount())); // original amount is preserved
    }

    @Test
    void partialPaymentMarksFinePartiallyPaid() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        paymentService.pay(u.getId(), fine.getId(), req("5.00"));
        assertEquals(FineStatus.PARTIALLY_PAID, reload(fine).getStatus());
        assertEquals(0, bd("15.00").compareTo(fineService.outstanding(reload(fine))));
    }

    @Test
    void failedPaymentIsRecordedAndLeavesBalanceUnchanged() {
        when(processor.process(any())).thenReturn(new PaymentResult(false, null));
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        Payment p = paymentService.pay(u.getId(), fine.getId(), req("20.00"));
        assertEquals(PaymentStatus.FAILED, p.getStatus());
        assertNull(p.getTransactionReference());
        assertEquals(1, paymentRepo.count());
        assertEquals(FineStatus.UNPAID, reload(fine).getStatus());
        assertEquals(0, bd("20.00").compareTo(fineService.outstanding(reload(fine))));
    }

    @Test
    void processorExceptionIsRecordedAsFailed() {
        when(processor.process(any())).thenThrow(new RuntimeException("gateway down"));
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        Payment p = paymentService.pay(u.getId(), fine.getId(), req("10.00"));
        assertEquals(PaymentStatus.FAILED, p.getStatus());
        assertEquals(0, bd("20.00").compareTo(fineService.outstanding(reload(fine))));
    }

    @Test
    void paymentAboveOutstandingIsRejected() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        assertThrows(BusinessException.class, () -> paymentService.pay(u.getId(), fine.getId(), req("20.01")));
        assertEquals(0, paymentRepo.count());
        paymentService.pay(u.getId(), fine.getId(), req("15.00"));
        assertThrows(BusinessException.class, () -> paymentService.pay(u.getId(), fine.getId(), req("5.01")));
    }

    @Test
    void zeroNegativeAndMalformedAmountsAreRejected() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        for (String bad : new String[]{"0", "0.00", "-1.00", "1.005", null}) {
            assertThrows(BusinessException.class, () -> paymentService.pay(u.getId(), fine.getId(), req(bad)), "amount " + bad);
        }
        assertEquals(0, paymentRepo.count());
    }

    @Test
    void paidFineCannotBePaidAgain() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        paymentService.pay(u.getId(), fine.getId(), req("20.00"));
        assertThrows(BusinessException.class, () -> paymentService.pay(u.getId(), fine.getId(), req("1.00")));
    }

    @Test
    void userCannotPayAnotherUsersFine() {
        User owner = newUser(Role.USER);
        User other = newUser(Role.USER);
        Fine fine = newFine(owner, "20.00");
        BusinessException e = assertThrows(BusinessException.class,
                () -> paymentService.pay(other.getId(), fine.getId(), req("5.00")));
        assertEquals(404, e.getStatus().value());
        assertEquals(0, paymentRepo.count());
        assertThrows(BusinessException.class, () -> fineService.ownedView(other.getId(), fine.getId()));
    }

    @Test
    void userCannotReadAnotherUsersPayment() {
        User owner = newUser(Role.USER);
        User other = newUser(Role.USER);
        Fine fine = newFine(owner, "20.00");
        Payment p = paymentService.pay(owner.getId(), fine.getId(), req("5.00"));
        assertThrows(BusinessException.class, () -> paymentService.ownedPayment(other.getId(), p.getId()));
        assertNotNull(paymentService.ownedPayment(owner.getId(), p.getId()));
        assertTrue(paymentService.userPayments(other.getId()).isEmpty());
    }

    @Test
    void sameIdempotencyKeyIsProcessedOnlyOnce() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        String key = UUID.randomUUID().toString();
        Payment first = paymentService.pay(u.getId(), fine.getId(), req("5.00", key));
        Payment second = paymentService.pay(u.getId(), fine.getId(), req("5.00", key));
        assertEquals(first.getId(), second.getId());
        assertEquals(1, paymentRepo.count());
        verify(processor, times(1)).process(any());
        assertEquals(0, bd("15.00").compareTo(fineService.outstanding(reload(fine))));
    }

    @Test
    void concurrentPaymentsCannotOverpay() throws Exception {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    paymentService.pay(u.getId(), fine.getId(), req("15.00"));
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int accepted = 0;
        for (Future<Boolean> f : results) {
            if (f.get(60, TimeUnit.SECONDS)) accepted++;
        }
        pool.shutdown();

        assertEquals(1, accepted, "only one 15.00 payment fits into a 20.00 fine");
        BigDecimal paid = paymentRepo.sumByStatus(PaymentStatus.SUCCESS);
        assertEquals(0, bd("15.00").compareTo(paid));
        assertEquals(0, bd("5.00").compareTo(fineService.outstanding(reload(fine))));
        assertEquals(FineStatus.PARTIALLY_PAID, reload(fine).getStatus());
    }

    // ---------- adjustments ----------

    private AdjustmentRequest adj(String amount, String reason) {
        AdjustmentRequest a = new AdjustmentRequest();
        a.setAmount(bd(amount));
        a.setReason(reason);
        return a;
    }

    @Test
    void librarianAdjustmentIsRecordedWithReasonAndLibrarian() {
        User u = newUser(Role.USER);
        User lib = newUser(Role.LIBRARIAN);
        Fine fine = newFine(u, "20.00");
        FineAdjustment a = adjustmentService.adjust(fine.getId(), lib.getId(), adj("5.00", "Book was damaged in transit"));
        assertEquals("Book was damaged in transit", a.getReason());
        assertEquals(lib.getId(), a.getLibrarian().getId());
        assertEquals(0, bd("15.00").compareTo(fineService.outstanding(reload(fine))));
        assertEquals(0, bd("20.00").compareTo(reload(fine).getAmount()));
    }

    @Test
    void fullWaiverMarksFineWaived() {
        User u = newUser(Role.USER);
        User lib = newUser(Role.LIBRARIAN);
        Fine fine = newFine(u, "20.00");
        adjustmentService.adjust(fine.getId(), lib.getId(), adj("20.00", "Library error"));
        assertEquals(FineStatus.WAIVED, reload(fine).getStatus());
    }

    @Test
    void nonLibrarianCannotAdjust() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        assertThrows(AccessDeniedException.class,
                () -> adjustmentService.adjust(fine.getId(), u.getId(), adj("5.00", "please")));
        assertEquals(0, adjustmentRepo.count());
    }

    @Test
    void adjustmentNeedsReasonAndValidAmount() {
        User u = newUser(Role.USER);
        User lib = newUser(Role.LIBRARIAN);
        Fine fine = newFine(u, "20.00");
        assertThrows(BusinessException.class, () -> adjustmentService.adjust(fine.getId(), lib.getId(), adj("5.00", "  ")));
        assertThrows(BusinessException.class, () -> adjustmentService.adjust(fine.getId(), lib.getId(), adj("0", "reason")));
        assertThrows(BusinessException.class, () -> adjustmentService.adjust(fine.getId(), lib.getId(), adj("-3", "reason")));
        assertThrows(BusinessException.class, () -> adjustmentService.adjust(fine.getId(), lib.getId(), adj("20.01", "reason")));
        assertEquals(0, adjustmentRepo.count());
    }

    // ---------- dashboard ----------

    @Test
    void dashboardCountsOnlySuccessfulPaymentsAsCollected() {
        User u = newUser(Role.USER);
        Fine fine = newFine(u, "20.00");
        paymentService.pay(u.getId(), fine.getId(), req("5.00")); // SUCCESS
        when(processor.process(any())).thenReturn(new PaymentResult(false, null));
        paymentService.pay(u.getId(), fine.getId(), req("7.00")); // FAILED
        Payment pending = new Payment();
        pending.setFine(reload(fine));
        pending.setAmount(bd("3.00"));
        pending.setMethod(PaymentMethod.CARD);
        pending.setStatus(PaymentStatus.PENDING);
        pending.setIdempotencyKey(UUID.randomUUID().toString());
        paymentRepo.save(pending);

        FeeStats s = fineService.stats();
        assertEquals(0, bd("20.00").compareTo(s.getTotalAssessed()));
        assertEquals(0, bd("5.00").compareTo(s.getTotalCollected()));
        assertEquals(0, bd("15.00").compareTo(s.getTotalOutstanding()));
        assertEquals(1, s.getPartiallyPaidCount());
        assertEquals(0, s.getUnpaidCount());
        assertEquals(1, s.getUsersWithOutstanding());
    }

    @Test
    void dashboardCountsOverdueLoans() {
        User u = newUser(Role.USER);
        newLoan(u, newBook(2), java.time.LocalDate.now().minusDays(2));
        newLoan(u, newBook(2), java.time.LocalDate.now().plusDays(5));
        assertEquals(1, fineService.stats().getOverdueLoans());
    }
}
