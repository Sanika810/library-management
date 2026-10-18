package com.library.service;

import com.library.entity.*;
import com.library.exception.BusinessException;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BorrowService {
    public static final int LOAN_DAYS = 14;

    private final BorrowRecordRepository recordRepo;
    private final BookRepository bookRepo;
    private final UserRepository userRepo;
    private final FineService fineService;

    @Transactional
    public BorrowRecord borrow(Long userId, Long bookId) {
        Book book = bookRepo.findByIdForUpdate(bookId)
                .orElseThrow(() -> BusinessException.notFound("Book not found"));
        User user = userRepo.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        if (book.getAvailableCopies() <= 0) {
            throw new BusinessException("No copies of this book are currently available");
        }
        if (recordRepo.existsByUserIdAndBookIdAndStatus(userId, bookId, BorrowStatus.BORROWED)) {
            throw new BusinessException("You have already borrowed this book");
        }
        LocalDate today = LocalDate.now();
        BorrowRecord r = new BorrowRecord();
        r.setUser(user);
        r.setBook(book);
        r.setBorrowDate(today);
        r.setDueDate(today.plusDays(LOAN_DAYS));
        r.setStatus(BorrowStatus.BORROWED);
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        return recordRepo.save(r);
    }

    /**
     * One transaction: status + return date, inventory, and fine creation commit or roll back together.
     * The borrow record row is locked first, so a double submit cannot return twice or create two fines
     * (fines.borrow_record_id UNIQUE is the final safety net).
     */
    @Transactional
    public BorrowRecord returnBook(Long recordId, Long userId) {
        BorrowRecord r = recordRepo.findByIdForUpdate(recordId)
                .orElseThrow(() -> BusinessException.notFound("Borrow record not found"));
        if (!r.getUser().getId().equals(userId)) {
            throw BusinessException.notFound("Borrow record not found");
        }
        if (r.getStatus() == BorrowStatus.RETURNED) {
            throw new BusinessException("This book has already been returned");
        }
        Book book = bookRepo.findByIdForUpdate(r.getBook().getId())
                .orElseThrow(() -> BusinessException.notFound("Book not found"));
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        r.setReturnDate(LocalDate.now());
        r.setStatus(BorrowStatus.RETURNED);
        fineService.createFineIfOverdue(r);
        return r;
    }

    public List<BorrowRecord> userRecords(Long userId) {
        return recordRepo.findByUserIdOrderByBorrowDateDescIdDesc(userId);
    }

    public List<BorrowRecord> allRecords() {
        return recordRepo.findAllByOrderByBorrowDateDescIdDesc();
    }
}
