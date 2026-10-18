package com.library;

import com.library.entity.*;
import com.library.exception.BusinessException;
import com.library.service.BorrowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BorrowReturnTest extends AbstractIntegrationTest {
    @Autowired BorrowService borrowService;

    @Test
    void borrowSetsFourteenDayDueDateAndReducesStock() {
        User user = newUser(Role.USER);
        Book book = bookRepo.save(freshBook(2));
        BorrowRecord r = borrowService.borrow(user.getId(), book.getId());
        assertEquals(LocalDate.now().plusDays(14), r.getDueDate());
        assertEquals(1, bookRepo.findById(book.getId()).orElseThrow().getAvailableCopies());
    }

    @Test
    void returnOnDueDateProducesNoFine() {
        User user = newUser(Role.USER);
        Book book = newBook(2);
        BorrowRecord r = newLoan(user, book, LocalDate.now());
        borrowService.returnBook(r.getId(), user.getId());
        assertTrue(fineRepo.findAll().isEmpty());
        assertEquals(2, bookRepo.findById(book.getId()).orElseThrow().getAvailableCopies());
    }

    @Test
    void returnFourDaysLateProducesTwentyRupeeFine() {
        User user = newUser(Role.USER);
        BorrowRecord r = newLoan(user, newBook(2), LocalDate.now().minusDays(4));
        borrowService.returnBook(r.getId(), user.getId());
        Fine fine = fineRepo.findByBorrowRecordId(r.getId()).orElseThrow();
        assertEquals(4, fine.getOverdueDays());
        assertEquals(0, bd("20.00").compareTo(fine.getAmount()));
        assertEquals(FineStatus.UNPAID, fine.getStatus());
    }

    @Test
    void oneDayLateIsFined() {
        User user = newUser(Role.USER);
        BorrowRecord r = newLoan(user, newBook(2), LocalDate.now().minusDays(1));
        borrowService.returnBook(r.getId(), user.getId());
        assertEquals(0, bd("5.00").compareTo(fineRepo.findByBorrowRecordId(r.getId()).orElseThrow().getAmount()));
    }

    @Test
    void configuredRateIsUsed() {
        fineService.updateRate(bd("7.50"));
        User user = newUser(Role.USER);
        BorrowRecord r = newLoan(user, newBook(2), LocalDate.now().minusDays(2));
        borrowService.returnBook(r.getId(), user.getId());
        assertEquals(0, bd("15.00").compareTo(fineRepo.findByBorrowRecordId(r.getId()).orElseThrow().getAmount()));
    }

    @Test
    void duplicateReturnCreatesNoSecondFineAndNoExtraStock() {
        User user = newUser(Role.USER);
        Book book = newBook(2);
        BorrowRecord r = newLoan(user, book, LocalDate.now().minusDays(3));
        borrowService.returnBook(r.getId(), user.getId());
        assertThrows(BusinessException.class, () -> borrowService.returnBook(r.getId(), user.getId()));
        assertEquals(1, fineRepo.count());
        assertEquals(2, bookRepo.findById(book.getId()).orElseThrow().getAvailableCopies());
    }

    @Test
    void cannotReturnAnotherUsersBook() {
        User owner = newUser(Role.USER);
        User other = newUser(Role.USER);
        BorrowRecord r = newLoan(owner, newBook(2), LocalDate.now().minusDays(3));
        assertThrows(BusinessException.class, () -> borrowService.returnBook(r.getId(), other.getId()));
        assertEquals(BorrowStatus.BORROWED, recordRepo.findById(r.getId()).orElseThrow().getStatus());
    }

    private Book freshBook(int copies) {
        Book b = new Book();
        b.setIsbn("ISBN-" + System.nanoTime());
        b.setTitle("Fresh");
        b.setAuthor("A");
        b.setTotalCopies(copies);
        b.setAvailableCopies(copies);
        return b;
    }
}
