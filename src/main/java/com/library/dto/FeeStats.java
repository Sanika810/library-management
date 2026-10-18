package com.library.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter @AllArgsConstructor
public class FeeStats {
    private final BigDecimal totalAssessed;
    private final BigDecimal totalAdjusted;
    private final BigDecimal totalCollected;
    private final BigDecimal totalOutstanding;
    private final long usersWithOutstanding;
    private final long unpaidCount;
    private final long partiallyPaidCount;
    private final long overdueLoans;
}
