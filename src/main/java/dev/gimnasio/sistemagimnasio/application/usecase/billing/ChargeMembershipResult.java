package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.domain.shared.Money;

/**
 * @param cashReceived solo en efectivo
 * @param change       solo en efectivo
 * @param repeated     {@code true} si la operación ya se había registrado y se devuelve el cobro original
 */
public record ChargeMembershipResult(
        PaymentRow payment,
        int durationDays,
        Money cashReceived,
        Money change,
        boolean repeated) {
}
