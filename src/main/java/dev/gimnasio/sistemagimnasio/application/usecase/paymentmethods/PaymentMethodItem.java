package dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods;

import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;

/** {@code cash}: al cobrar se pide lo recibido y se calcula el cambio. */
public record PaymentMethodItem(long id, String name, boolean cash, boolean active) {
    public static PaymentMethodItem of(PaymentMethod method) {
        return new PaymentMethodItem(method.id(), method.name(), method.cash(), method.active());
    }
}
