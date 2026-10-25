package com.billetterie.payment_service.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSimulatorTest {

    @Test
    void withZeroFailureRate_alwaysApproves() {
        PaymentSimulator simulator = new PaymentSimulator(0.0);

        for (int i = 0; i < 200; i++) {
            assertThat(simulator.approve()).isTrue();
        }
    }

    @Test
    void withFullFailureRate_neverApproves() {
        PaymentSimulator simulator = new PaymentSimulator(1.0);

        for (int i = 0; i < 200; i++) {
            assertThat(simulator.approve()).isFalse();
        }
    }

    @Test
    void rejectsFailureRateOutsideZeroToOne() {
        assertThatThrownBy(() -> new PaymentSimulator(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentSimulator(-0.1)).isInstanceOf(IllegalArgumentException.class);
    }
}