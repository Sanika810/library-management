package com.library.dto;

import com.library.entity.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Only these three fields are accepted from the client. User, fine owner, status and result are server-side. */
@Getter @Setter
public class PaymentRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 8, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Select a payment method")
    private PaymentMethod method;

    @NotBlank
    @Size(max = 40)
    private String idempotencyKey;
}
