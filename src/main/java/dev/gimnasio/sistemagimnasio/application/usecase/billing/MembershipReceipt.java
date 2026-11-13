package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.domain.shared.Money;

/** El recibo de un cobro; {@code cashReceived} y {@code change} solo en efectivo. */
public record MembershipReceipt(GymHeader header, PaymentRow payment, int durationDays, Money cashReceived,
                                Money change) {
}
