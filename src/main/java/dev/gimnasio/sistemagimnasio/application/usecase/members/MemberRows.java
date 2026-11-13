package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.MembershipRules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Arma {@link MemberRow}; el estado sale de {@link MembershipRules}, nunca de la base. */
public final class MemberRows {
    private MemberRows() {
    }

    /**
     * @param latest   la última membresía, o null si nunca ha pagado
     * @param planName el plan de esa membresía, o null
     */
    public static MemberRow of(Member member, Membership latest, String planName, LocalDate today, int warningDays,
                               int autoDeactivationDays) {
        MemberStatus status = MembershipRules.statusOf(latest, today, warningDays, autoDeactivationDays);
        LocalDate end = latest == null ? null : latest.end();
        boolean covered = status == MemberStatus.VIGENTE || status == MemberStatus.POR_VENCER;
        boolean overdue = status == MemberStatus.VENCIDA || status == MemberStatus.BAJA;
        return new MemberRow(
                member.id(),
                member.fullName(),
                member.phone().value(),
                member.email() == null ? null : member.email().value(),
                status,
                planName,
                end,
                covered ? (int) ChronoUnit.DAYS.between(today, end) : null,
                overdue ? (int) ChronoUnit.DAYS.between(end, today) : null,
                status == MemberStatus.VENCIDA ? end.plusDays(autoDeactivationDays) : null,
                status == MemberStatus.BAJA ? end : null);
    }
}
