package com.library.controller;

import com.library.dto.FineView;
import com.library.service.FineService;
import com.library.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.function.Function;

/** User-facing, read-only. Users can never change fine amounts or status. */
@Controller
@RequiredArgsConstructor
public class FineController {
    private final FineService fineService;
    private final UserService userService;

    @GetMapping("/user/fines")
    public String fines(Principal principal, Model model) {
        List<FineView> fines = fineService.userFines(userService.currentId(principal));
        model.addAttribute("fines", fines);
        model.addAttribute("totalOriginal", sum(fines, v -> v.getFine().getAmount()));
        model.addAttribute("totalAdjusted", sum(fines, FineView::getAdjusted));
        model.addAttribute("totalPaid", sum(fines, FineView::getPaid));
        model.addAttribute("totalOutstanding", sum(fines, FineView::getOutstanding));
        return "user/fines";
    }

    private static BigDecimal sum(List<FineView> list, Function<FineView, BigDecimal> f) {
        return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
