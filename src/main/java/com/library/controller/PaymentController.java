package com.library.controller;

import com.library.dto.FineView;
import com.library.dto.PaymentRequest;
import com.library.entity.Payment;
import com.library.entity.PaymentMethod;
import com.library.exception.BusinessException;
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

import java.security.Principal;
import java.util.UUID;

/** The authenticated user is always taken from the session, never from request parameters. */
@Controller
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final FineService fineService;
    private final UserService userService;

    @GetMapping("/user/fines/{fineId}/pay")
    public String payForm(@PathVariable Long fineId, Principal principal, Model model) {
        FineView view = fineService.ownedView(userService.currentId(principal), fineId);
        if (view.getOutstanding().signum() <= 0) {
            throw new BusinessException("This fine has no outstanding balance");
        }
        PaymentRequest form = new PaymentRequest();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        form.setAmount(view.getOutstanding());
        model.addAttribute("form", form);
        model.addAttribute("view", view);
        model.addAttribute("methods", PaymentMethod.values());
        return "user/pay-fine";
    }

    @PostMapping("/user/fines/{fineId}/pay")
    public String pay(@PathVariable Long fineId, @Valid @ModelAttribute("form") PaymentRequest form,
                      BindingResult br, Principal principal, Model model) {
        Long uid = userService.currentId(principal);
        model.addAttribute("view", fineService.ownedView(uid, fineId));
        model.addAttribute("methods", PaymentMethod.values());
        if (br.hasErrors()) {
            return "user/pay-fine";
        }
        try {
            Payment payment = paymentService.pay(uid, fineId, form);
            return "redirect:/user/payments/" + payment.getId();
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw e;
            }
            model.addAttribute("flashErr", e.getMessage());
            return "user/pay-fine";
        }
    }

    @GetMapping("/user/payments")
    public String history(Principal principal, Model model) {
        model.addAttribute("payments", paymentService.userPayments(userService.currentId(principal)));
        return "user/payment-history";
    }

    @GetMapping("/user/payments/{id}")
    public String result(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("payment", paymentService.ownedPayment(userService.currentId(principal), id));
        return "user/payment-result";
    }
}
