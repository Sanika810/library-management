package com.library.controller;

import com.library.dto.BookForm;
import com.library.exception.BusinessException;
import com.library.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class BookController {
    private final BookService bookService;

    @GetMapping("/user/books")
    public String catalog(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("books", bookService.search(q));
        model.addAttribute("q", q);
        return "user/books";
    }

    @GetMapping("/librarian/books")
    public String list(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("books", bookService.search(q));
        model.addAttribute("q", q);
        return "librarian/books";
    }

    @GetMapping("/librarian/books/new")
    public String newForm(Model model) {
        model.addAttribute("form", new BookForm());
        return "librarian/book-form";
    }

    @GetMapping("/librarian/books/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", bookService.toForm(bookService.get(id)));
        return "librarian/book-form";
    }

    @PostMapping("/librarian/books")
    public String save(@Valid @ModelAttribute("form") BookForm form, BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "librarian/book-form";
        }
        try {
            bookService.save(form);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw e;
            }
            br.reject("business", e.getMessage());
            return "librarian/book-form";
        }
        ra.addFlashAttribute("flashOk", "Book saved.");
        return "redirect:/librarian/books";
    }

    @PostMapping("/librarian/books/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookService.delete(id);
            ra.addFlashAttribute("flashOk", "Book deleted.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/librarian/books";
    }
}
