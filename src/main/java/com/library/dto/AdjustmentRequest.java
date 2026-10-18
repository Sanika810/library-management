package com.library.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class AdjustmentRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 8, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "A reason is required")
    @Size(max = 255)
    private String reason;
}
