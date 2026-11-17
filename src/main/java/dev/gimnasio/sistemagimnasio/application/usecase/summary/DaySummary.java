package dev.gimnasio.sistemagimnasio.application.usecase.summary;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * El tablero: cuántos socios están cubiertos, quiénes vencen pronto, quiénes ya vencieron y lo que se cobró hoy.
 *
 * @param weekEnd               domingo de esta semana
 * @param activeMembers         vigentes y por vencer
 * @param expiringCount         por vencer
 * @param expiringThisWeekCount cubiertos cuyo vencimiento cae de hoy a {@code weekEnd}
 * @param expiredCount          vencidos que aún no pasan a baja
 * @param expiring              por vencer, del vencimiento más próximo al más lejano
 * @param expired               vencidos, del vencimiento más reciente al más antiguo; cada uno con su día de baja
 * @param recentPayments        últimos cobros, el más reciente primero
 */
public record DaySummary(
        LocalDate today,
        LocalDate weekEnd,
        int activeMembers,
        int expiringCount,
        int expiringThisWeekCount,
        int expiredCount,
        Money membershipIncomeToday,
        int paymentsToday,
        List<MemberRow> expiring,
        List<MemberRow> expired,
        List<PaymentRow> recentPayments) {

    public DaySummary {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(weekEnd, "weekEnd");
        Objects.requireNonNull(membershipIncomeToday, "membershipIncomeToday");
        expiring = List.copyOf(expiring);
        expired = List.copyOf(expired);
        recentPayments = List.copyOf(recentPayments);
    }
}
