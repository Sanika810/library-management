package com.library.payment;

import com.library.entity.Payment;

/**
 * Boundary to a payment provider. Only decides success/failure and returns a reference;
 * it never touches fines or balances. A real gateway = a new implementation of this interface.
 */
public interface PaymentProcessor {
    PaymentResult process(Payment payment);
}
