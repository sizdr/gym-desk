package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lo que se puede calcular con los datos que ya hay. Los opcionales son null mientras falte lo necesario.
 *
 * @param total   precio del plan, o 0 sin plan
 * @param change  solo en efectivo, si lo recibido alcanza
 * @param missing solo en efectivo, si lo recibido no alcanza
 * @param errors  los mismos campos y mensajes que lanzaría el cobro
 */
public record ChargePreview(
        MemberRow member,
        PlanOption plan,
        LocalDate newStart,
        LocalDate newEnd,
        ChargeKind kind,
        Money total,
        PaymentMethodItem paymentMethod,
        Money cashReceived,
        Money change,
        Money missing,
        Map<String, String> errors,
        boolean ready) {

    public ChargePreview {
        errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
