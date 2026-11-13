package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;

import java.util.List;

/** Solo planes y métodos activos. */
public record ChargeOptions(List<PlanOption> plans, List<PaymentMethodItem> paymentMethods) {
    public ChargeOptions {
        plans = List.copyOf(plans);
        paymentMethods = List.copyOf(paymentMethods);
    }
}
