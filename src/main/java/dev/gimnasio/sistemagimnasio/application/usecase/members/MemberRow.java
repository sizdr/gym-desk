package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;

import java.time.LocalDate;

/**
 * Un socio en una lista, con lo necesario para mostrar su vigencia sin calcular nada en la UI.
 *
 * @param email            opcional
 * @param planName         plan de la última membresía; null si nunca ha pagado
 * @param membershipEnd    vencimiento de la última membresía; null si nunca ha pagado
 * @param daysLeft         solo cubiertos: días de hoy al vencimiento (0 = vence hoy)
 * @param daysOverdue      solo vencidos y en baja: días desde el vencimiento
 * @param deactivatesOn    solo vencidos: día en que pasará a baja si no renueva
 * @param deactivationDate solo en baja: igual al vencimiento
 */
public record MemberRow(
        long id,
        String fullName,
        String phone,
        String email,
        MemberStatus status,
        String planName,
        LocalDate membershipEnd,
        Integer daysLeft,
        Integer daysOverdue,
        LocalDate deactivatesOn,
        LocalDate deactivationDate) {
}
