package com.library.controller;

import com.library.dto.AdjustmentRequest;
import com.library.dto.FineView;
import com.library.entity.FineStatus;
import com.library.entity.PaymentStatus;
import com.library.exception.BusinessException;
import com.library.service.FineAdjustmentService;
import com.library.service.FineService;
import com.library.service.PaymentService;
import com.library.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;

/** All /librarian/** URLs require ROLE_LIBRARIAN (SecurityConfig); FineAdjustmentService re-checks the role. */
@Controller
@RequestMapping("/librarian")
@RequiredArgsConstructor
public class LibrarianFineController {
    private final FineService fineService;
    private final PaymentService paymentService;
    private final FineAdjustmentService adjustmentService;
    private final UserService userService;

    @GetMapping("/fines")
    public String fines(@RequestParam(defaultValue = "") String q,
                        @RequestParam(required = false) FineStatus status, Model model) {
        model.addAttribute("fines", fineService.search(q, status));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("statuses", FineStatus.values());
        return "librarian/fines";
    }

    @GetMapping("/payments")
    public String payments(@RequestParam(required = false) PaymentStatus status, Model model) {
        model.addAttribute("payments", paymentService.all(status));
        model.addAttribute("status", status);
        model.addAttribute("statuses", PaymentStatus.values());
        return "librarian/payments";
    }

    @GetMapping("/fee-dashboard")
    public String feeDashboard(Model model) {
        model.addAttribute("stats", fineService.stats());
        model.addAttribute("rate", fineService.currentRate());
        return "librarian/fee-dashboard";
    }

    @PostMapping("/fines/rate")
    public String updateRate(@RequestParam BigDecimal ratePerDay, RedirectAttributes ra) {
        try {
            fineService.updateRate(ratePerDay);
            ra.addFlashAttribute("flashOk", "Fine rate updated. It applies to books returned from now on.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("flashErr", e.getMessage());
        }
        return "redirect:/librarian/fee-dashboard";
    }

    @GetMapping("/fines/{id}/adjust")
    public String adjustForm(@PathVariable Long id, Model model) {
        loadAdjustModel(id, model);
        model.addAttribute("form", new AdjustmentRequest());
        return "librarian/adjust-fine";
    }

    @PostMapping("/fines/{id}/adjust")
    public String adjust(@PathVariable Long id, @Valid @ModelAttribute("form") AdjustmentRequest form,
                         BindingResult br, Principal principal, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            loadAdjustModel(id, model);
            return "librarian/adjust-fine";
        }
        try {
            adjustmentService.adjust(id, userService.currentId(principal), form);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw e;
            }
            loadAdjustModel(id, model);
            model.addAttribute("flashErr", e.getMessage());
            return "librarian/adjust-fine";
        }
        ra.addFlashAttribute("flashOk", "Fine adjustment recorded.");
        return "redirect:/librarian/fines";
    }

    private void loadAdjustModel(Long id, Model model) {
        FineView view = fineService.get(id);
        model.addAttribute("view", view);
        model.addAttribute("history", adjustmentService.history(id));
    }
}
