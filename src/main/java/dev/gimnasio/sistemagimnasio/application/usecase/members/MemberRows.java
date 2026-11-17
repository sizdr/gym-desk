package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.MembershipRules;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Arma {@link MemberRow}; el estado sale de {@link MembershipRules}, nunca de la base. */
public final class MemberRows {
    private MemberRows() {
    }

    /** Todos los socios con su estado de {@code today}, en el orden del repositorio. */
    public static List<MemberRow> all(MemberRepository members, MembershipRepository memberships,
                                      PlanRepository plans, GymSettings settings, LocalDate today) {
        Map<Long, Membership> latest = memberships.findLatestPerMember().stream()
                .collect(Collectors.toMap(Membership::memberId, Function.identity(),
                        (a, b) -> a.end().isAfter(b.end()) ? a : b));
        Map<Long, String> planNames = plans.findAll().stream().collect(Collectors.toMap(Plan::id, Plan::name));
        return members.findAll().stream().map(member -> {
            Membership last = latest.get(member.id());
            return of(member, last, last == null ? null : planNames.get(last.planId()), today,
                    settings.warningDays(), settings.autoDeactivationDays());
        }).toList();
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
                status == MemberStatus.BAJA ? end : null,
                member.joinDate());
    }
}
