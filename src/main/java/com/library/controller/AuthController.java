package com.library.controller;

import com.library.dto.RegisterRequest;
import com.library.exception.BusinessException;
import com.library.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form, BindingResult br,
                           RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form);
        } catch (BusinessException e) {
            br.rejectValue("email", "duplicate", e.getMessage());
            return "auth/register";
        }
        ra.addFlashAttribute("flashOk", "Registration successful. Please log in.");
        return "redirect:/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth) {
        boolean librarian = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"));
        return librarian ? "redirect:/librarian/dashboard" : "redirect:/user/dashboard";
    }
}
