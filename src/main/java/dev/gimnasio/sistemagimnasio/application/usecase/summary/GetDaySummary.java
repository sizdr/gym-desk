package dev.gimnasio.sistemagimnasio.application.usecase.summary;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentReader;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRows;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Control de vigencias del día. El estado de cada socio se calcula con las mismas reglas que Socios
 * ({@link MemberRows}), así que el tablero y la lista nunca se contradicen.
 */
public final class GetDaySummary {
    static final int RECENT_PAYMENTS = 8;

    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final SettingsRepository settings;
    private final MembershipPaymentRepository payments;
    private final PaymentReader reader;
    private final Authorizer authorizer;
    private final Clock clock;

    public GetDaySummary(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
                         SettingsRepository settings, MembershipPaymentRepository payments,
                         PaymentMethodRepository methods, UserRepository users, Authorizer authorizer, Clock clock) {
        this.members = Objects.requireNonNull(members, "members");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.reader = new PaymentReader(payments, memberships, members, plans, methods, users);
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DaySummary execute(User actor) {
        authorizer.require(actor, Permission.VIEW_DAILY_SUMMARY);
        LocalDate today = LocalDate.now(clock);
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<MemberRow> rows = MemberRows.all(members, memberships, plans, settings.get(), today);

        Comparator<MemberRow> byName = Comparator.comparing(MemberRow::fullName);
        List<MemberRow> expiring = rows.stream()
                .filter(row -> row.status() == MemberStatus.POR_VENCER)
                .sorted(Comparator.comparing(MemberRow::membershipEnd).thenComparing(byName))
                .toList();
        List<MemberRow> expired = rows.stream()
                .filter(row -> row.status() == MemberStatus.VENCIDA)
                .sorted(Comparator.comparing(MemberRow::membershipEnd, Comparator.reverseOrder()).thenComparing(byName))
                .toList();
        int active = (int) rows.stream().filter(GetDaySummary::covered).count();
        int thisWeek = (int) rows.stream()
                .filter(row -> covered(row) && !row.membershipEnd().isAfter(weekEnd))
                .count();

        MembershipPaymentRepository.Totals totals = payments.totalsBetween(today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        List<PaymentRow> recent = payments.findRecentIds(RECENT_PAYMENTS).stream()
                .map(reader::read)
                .flatMap(Optional::stream)
                .map(PaymentReader.View::row)
                .toList();

        return new DaySummary(today, weekEnd, active, expiring.size(), thisWeek, expired.size(), totals.amount(),
                totals.count(), expiring, expired, recent);
    }

    private static boolean covered(MemberRow row) {
        return row.status() == MemberStatus.VIGENTE || row.status() == MemberStatus.POR_VENCER;
    }
}
