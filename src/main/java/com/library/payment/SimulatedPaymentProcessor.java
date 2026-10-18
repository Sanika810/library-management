package com.library.payment;

import com.library.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * DEMONSTRATION ONLY. No real UPI/card/bank transaction happens. Randomly fails
 * with probability app.payment.simulated.failure-rate to exercise the failure path.
 */
@Component
public class SimulatedPaymentProcessor implements PaymentProcessor {

    @Value("${app.payment.simulated.failure-rate:0.1}")
    private double failureRate;

    private final SecureRandom random = new SecureRandom();

    @Override
    public PaymentResult process(Payment payment) {
        if (random.nextDouble() < failureRate) {
            return new PaymentResult(false, null);
        }
        String ref = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return new PaymentResult(true, ref);
    }
}
