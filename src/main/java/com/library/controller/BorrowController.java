package com.library.controller;

import com.library.dto.FineView;
import com.library.entity.BorrowRecord;
import com.library.entity.Fine;
import com.library.exception.BusinessException;
import com.library.service.BorrowService;
import com.library.service.FineService;
import com.library.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class BorrowController {
    private final BorrowService borrowService;
    private final FineService fineService;
    private final UserService userService;

    @GetMapping("/user/dashboard")
    public String dashboard(Principal principal, Model model) {
        Long uid = userService.currentId(principal);
        BigDecimal outstanding = fineService.userFines(uid).stream()
                .map(FineView::getOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("records", borrowService.userRecords(uid));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("outstandingTotal", outstanding);
        return "user/dashboard";
    }

    @PostMapping("/user/borrow/{bookId}")
    public String borrow(@PathVariable Long bookId, Principal principal, RedirectAttributes ra) {
        Long uid = userService.currentId(principal);
        try {
            BorrowRecord r = borrowService.borrow(uid, bookId);
            ra.addFlashAttribute("flashOk",
                    "Borrowed \"" + r.getBook().getTitle() + "\". Due on " + r.getDueDate() + ".");
            return "redirect:/user/dashboard";
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw e;
            }
            ra.addFlashAttribute("flashErr", e.getMessage());
            return "redirect:/user/books";
        }
    }

    @PostMapping("/user/return/{recordId}")
    public String returnBook(@PathVariable Long recordId, Principal principal, RedirectAttributes ra) {
        Long uid = userService.currentId(principal);
        try {
            borrowService.returnBook(recordId, uid);
            Optional<Fine> fine = fineService.forRecord(recordId);
            if (fine.isPresent()) {
                ra.addFlashAttribute("flashErr", "Book returned late (" + fine.get().getOverdueDays()
                        + " days overdue). A fine of " + fine.get().getAmount() + " was added. See My Fines.");
            } else {
                ra.addFlashAttribute("flashOk", "Book returned on time. No fine.");
            }
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw e;
            }
            ra.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/user/dashboard";
    }
}
