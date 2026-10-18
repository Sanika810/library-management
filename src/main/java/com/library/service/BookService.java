package com.library.service;

import com.library.dto.BookForm;
import com.library.entity.Book;
import com.library.exception.BusinessException;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {
    private final BookRepository bookRepo;
    private final BorrowRecordRepository recordRepo;

    public List<Book> search(String q) {
        return bookRepo.search(q == null ? "" : q.trim());
    }

    public Book get(Long id) {
        return bookRepo.findById(id).orElseThrow(() -> BusinessException.notFound("Book not found"));
    }

    public BookForm toForm(Book b) {
        BookForm f = new BookForm();
        f.setId(b.getId());
        f.setIsbn(b.getIsbn());
        f.setTitle(b.getTitle());
        f.setAuthor(b.getAuthor());
        f.setCategory(b.getCategory());
        f.setTotalCopies(b.getTotalCopies());
        return f;
    }

    @Transactional
    public Book save(BookForm f) {
        String isbn = f.getIsbn().trim();
        Book book;
        if (f.getId() == null) {
            if (bookRepo.findByIsbn(isbn).isPresent()) {
                throw new BusinessException("A book with this ISBN already exists");
            }
            book = new Book();
            book.setTotalCopies(f.getTotalCopies());
            book.setAvailableCopies(f.getTotalCopies());
        } else {
            book = bookRepo.findByIdForUpdate(f.getId())
                    .orElseThrow(() -> BusinessException.notFound("Book not found"));
            if (bookRepo.findByIsbn(isbn).filter(o -> !o.getId().equals(book.getId())).isPresent()) {
                throw new BusinessException("A book with this ISBN already exists");
            }
            int borrowed = book.getTotalCopies() - book.getAvailableCopies();
            if (f.getTotalCopies() < borrowed) {
                throw new BusinessException("Total copies cannot be below the " + borrowed + " copies currently borrowed");
            }
            book.setTotalCopies(f.getTotalCopies());
            book.setAvailableCopies(f.getTotalCopies() - borrowed);
        }
        book.setIsbn(isbn);
        book.setTitle(f.getTitle().trim());
        book.setAuthor(f.getAuthor().trim());
        book.setCategory(f.getCategory() == null || f.getCategory().isBlank() ? null : f.getCategory().trim());
        return bookRepo.save(book);
    }

    /** Books with borrow history cannot be deleted (history and fines must stay intact). */
    @Transactional
    public void delete(Long id) {
        Book b = get(id);
        if (recordRepo.existsByBookId(id)) {
            throw new BusinessException("This book has borrow history and cannot be deleted");
        }
        bookRepo.delete(b);
    }
}
