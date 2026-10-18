package com.library.controller;

import com.library.entity.BorrowStatus;
import com.library.entity.Role;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.UserRepository;
import com.library.service.BorrowService;
import com.library.service.FineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class LibrarianController {
    private final BookRepository bookRepo;
    private final UserRepository userRepo;
    private final BorrowRecordRepository recordRepo;
    private final BorrowService borrowService;
    private final FineService fineService;

    @GetMapping("/librarian/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("bookCount", bookRepo.count());
        model.addAttribute("memberCount", userRepo.countByRole(Role.USER));
        model.addAttribute("activeLoans", recordRepo.countByStatus(BorrowStatus.BORROWED));
        model.addAttribute("stats", fineService.stats());
        return "librarian/dashboard";
    }

    @GetMapping("/librarian/borrows")
    public String borrows(Model model) {
        model.addAttribute("records", borrowService.allRecords());
        model.addAttribute("today", LocalDate.now());
        return "librarian/borrows";
    }
}
