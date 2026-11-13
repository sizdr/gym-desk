package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un cobro de membresía; {@code start}–{@code end} es el periodo pagado. */
public record PaymentRow(
        long id,
        String folio,
        LocalDateTime dateTime,
        long memberId,
        String memberName,
        String planName,
        LocalDate start,
        LocalDate end,
        ChargeKind kind,
        String paymentMethodName,
        Money amount,
        String cashierName) {
}
