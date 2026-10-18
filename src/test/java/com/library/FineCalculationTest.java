package com.library;

import com.library.service.FineService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FineCalculationTest {

    @Test
    void returnedOnDueDateHasNoOverdueDays() {
        assertEquals(0, FineService.overdueDays(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)));
    }

    @Test
    void returnedEarlyHasNoOverdueDays() {
        assertEquals(0, FineService.overdueDays(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 10)));
    }

    @Test
    void oneDayLateIsOneOverdueDay() {
        assertEquals(1, FineService.overdueDays(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16)));
    }

    @Test
    void exampleFromSpecIsFourDaysAndTwentyRupees() {
        int days = FineService.overdueDays(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 19));
        assertEquals(4, days);
        assertEquals(0, new BigDecimal("20.00").compareTo(FineService.fineAmount(days, new BigDecimal("5.00"))));
    }

    @Test
    void monthYearAndLeapDayBoundaries() {
        assertEquals(3, FineService.overdueDays(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2)));
        assertEquals(2, FineService.overdueDays(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1)));
        assertEquals(1, FineService.overdueDays(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 1)));
    }

    @Test
    void amountUsesBigDecimalWithTwoDecimals() {
        assertEquals(new BigDecimal("13.50"), FineService.fineAmount(3, new BigDecimal("4.5")));
        assertEquals(new BigDecimal("0.00"), FineService.fineAmount(0, new BigDecimal("5.00")));
    }
}
