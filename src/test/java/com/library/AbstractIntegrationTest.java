package com.library;

import com.library.entity.*;
import com.library.payment.PaymentProcessor;
import com.library.payment.PaymentResult;
import com.library.repository.*;
import com.library.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Shared setup: real services + H2 database, the payment processor is a mock (success by default). */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {
    @Autowired protected UserRepository userRepo;
    @Autowired protected BookRepository bookRepo;
    @Autowired protected BorrowRecordRepository recordRepo;
    @Autowired protected FineRepository fineRepo;
    @Autowired protected PaymentRepository paymentRepo;
    @Autowired protected FineAdjustmentRepository adjustmentRepo;
    @Autowired protected FineService fineService;
    @Autowired protected PasswordEncoder encoder;

    @MockBean protected PaymentProcessor processor;

    @BeforeEach
    void cleanDatabase() {
        adjustmentRepo.deleteAll();
        paymentRepo.deleteAll();
        fineRepo.deleteAll();
        recordRepo.deleteAll();
        bookRepo.deleteAll();
        userRepo.deleteAll();
        fineService.updateRate(new BigDecimal("5.00"));
        when(processor.process(any())).thenAnswer(i -> new PaymentResult(true, "SIM-" + UUID.randomUUID()));
    }

    protected User newUser(Role role) {
        User u = new User();
        u.setName(role.name().toLowerCase());
        u.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.com");
        u.setPassword(encoder.encode("secret1"));
        u.setRole(role);
        return userRepo.save(u);
    }

    protected Book newBook(int copies) {
        Book b = new Book();
        b.setIsbn("ISBN-" + UUID.randomUUID().toString().substring(0, 12));
        b.setTitle("Test Book");
        b.setAuthor("Author");
        b.setTotalCopies(copies);
        b.setAvailableCopies(copies - 1); // one copy is on loan
        return bookRepo.save(b);
    }

    /** An active loan (book already taken) with the given due date. */
    protected BorrowRecord newLoan(User user, Book book, LocalDate dueDate) {
        BorrowRecord r = new BorrowRecord();
        r.setUser(user);
        r.setBook(book);
        r.setBorrowDate(dueDate.minusDays(14));
        r.setDueDate(dueDate);
        r.setStatus(BorrowStatus.BORROWED);
        return recordRepo.save(r);
    }

    /** A returned loan with a fine of the given amount (UNPAID, 1 overdue day at that rate). */
    protected Fine newFine(User user, String amount) {
        Book book = newBook(2);
        BorrowRecord r = newLoan(user, book, LocalDate.now().minusDays(10));
        r.setReturnDate(LocalDate.now());
        r.setStatus(BorrowStatus.RETURNED);
        recordRepo.save(r);
        Fine f = new Fine();
        f.setBorrowRecord(r);
        f.setOverdueDays(10);
        f.setRatePerDay(new BigDecimal(amount).divide(BigDecimal.TEN));
        f.setAmount(new BigDecimal(amount));
        f.setStatus(FineStatus.UNPAID);
        return fineRepo.save(f);
    }

    protected static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
