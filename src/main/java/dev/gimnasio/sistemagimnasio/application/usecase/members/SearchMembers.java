package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Buscar, filtrar por estado y ordenar socios. El estado no se guarda: se calcula hoy con la última membresía
 * de cada socio y los plazos de la configuración, así que filtrar y ordenar por estado ocurre aquí.
 */
public final class SearchMembers {
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final SettingsRepository settings;
    private final Authorizer authorizer;
    private final Clock clock;

    public SearchMembers(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
                         SettingsRepository settings, Authorizer authorizer, Clock clock) {
        this.members = Objects.requireNonNull(members, "members");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MemberSearchResult execute(User actor, SearchMembersQuery query) {
        authorizer.require(actor, Permission.VIEW_MEMBERS);
        LocalDate today = LocalDate.now(clock);
        List<MemberRow> rows = MemberRows.all(members, memberships, plans, settings.get(), today);

        Map<MemberStatus, Integer> counts = new EnumMap<>(MemberStatus.class);
        for (MemberStatus status : MemberStatus.values()) {
            counts.put(status, 0);
        }
        rows.forEach(row -> counts.merge(row.status(), 1, Integer::sum));

        String text = query.text() == null ? "" : query.text().strip();
        List<MemberRow> matching = rows.stream()
                .filter(row -> query.status() == null || row.status() == query.status())
                .filter(row -> matches(row, text))
                .sorted(order(query.sort() == null ? MemberSort.VENCIMIENTO : query.sort()))
                .toList();
        int limit = query.limit() == null ? matching.size() : Math.max(0, Math.min(query.limit(), matching.size()));
        return new MemberSearchResult(matching.subList(0, limit), matching.size(), rows.size(), counts);
    }

    /** Nombre parcial sin mayúsculas ni acentos, correo, o con 3 dígitos o más, teléfono. */
    static boolean matches(MemberRow row, String text) {
        if (text.isEmpty()) {
            return true;
        }
        String needle = normalize(text);
        if (normalize(row.fullName()).contains(needle)) {
            return true;
        }
        if (row.email() != null && row.email().contains(text.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String digits = text.replaceAll("\\D", "");
        return digits.length() >= 3 && digits.length() == text.replaceAll("[\\s-]", "").length()
                && row.phone().contains(digits);
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static Comparator<MemberRow> order(MemberSort sort) {
        Comparator<MemberRow> byName = Comparator.comparing(row -> normalize(row.fullName()));
        return switch (sort) {
            case NOMBRE -> byName;
            case VENCIMIENTO_DESC -> Comparator.comparing((MemberRow row) -> row.membershipEnd() == null)
                    .thenComparing(MemberRow::membershipEnd, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byName);
            case VENCIMIENTO -> Comparator.comparingInt(SearchMembers::group)
                    .thenComparing(row -> group(row) == 0 ? row.membershipEnd().toEpochDay()
                            : row.membershipEnd() == null ? 0 : -row.membershipEnd().toEpochDay())
                    .thenComparing(byName);
        };
    }

    /** 0 cubiertos, 1 vencidos y bajas, 2 sin membresía. */
    private static int group(MemberRow row) {
        return switch (row.status()) {
            case VIGENTE, POR_VENCER -> 0;
            case VENCIDA, BAJA -> 1;
            case SIN_MEMBRESIA -> 2;
        };
    }
}
