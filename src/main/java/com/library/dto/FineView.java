package com.library.dto;

import com.library.entity.Fine;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/** Fine plus its derived money figures: original (fine.amount), adjusted (waived), paid (SUCCESS only), outstanding. */
@Getter @AllArgsConstructor
public class FineView {
    private final Fine fine;
    private final BigDecimal adjusted;
    private final BigDecimal paid;
    private final BigDecimal outstanding;
}
