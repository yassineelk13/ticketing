package com.billetterie.payment_service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class PaymentSimulator {

    private final double failureRate;

    public PaymentSimulator(@Value("${app.payment.failure-rate:0.2}") double failureRate) {
        if (failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("app.payment.failure-rate must be between 0 and 1");
        }
        this.failureRate = failureRate;
    }

    public boolean approve() {
        return ThreadLocalRandom.current().nextDouble() >= failureRate;
    }
}