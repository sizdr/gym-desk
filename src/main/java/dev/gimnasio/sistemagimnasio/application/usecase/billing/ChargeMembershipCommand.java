package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.time.LocalDate;

/**
 * El mismo command sirve para la vista previa (puede venir incompleto) y para cobrar.
 *
 * @param operationId  lo genera la UI al abrir el formulario; con el mismo id no se cobra dos veces
 * @param cashReceived solo cuenta si el método es efectivo
 * @param start        opcional; solo en la primera membresía. null empieza hoy
 */
public record ChargeMembershipCommand(
        String operationId,
        Long memberId,
        Long planId,
        Long paymentMethodId,
        Money cashReceived,
        LocalDate start) {
}
