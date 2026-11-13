package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRows;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.MembershipRules;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que vale un cobro con los datos capturados: lo comparten la vista previa y el cobro, para que los dos
 * calculen y validen exactamente igual. Nunca lanza por datos incompletos; deja los errores en {@link #errors}.
 */
record ChargeDraft(
        Member member,
        boolean memberMissing,
        MemberRow memberRow,
        Plan plan,
        PaymentMethod method,
        ChargeKind kind,
        LocalDate start,
        LocalDate end,
        Money total,
        Money cashReceived,
        Money change,
        Money missing,
        Map<String, String> errors) {

    static final String MEMBER_REQUIRED = "Elige al socio que va a pagar.";
    static final String MEMBER_NOT_FOUND = "El socio no existe.";
    static final String PLAN_REQUIRED = "Elige un plan.";
    static final String PLAN_INACTIVE = "Ese plan está inactivo; elige otro.";
    static final String METHOD_REQUIRED = "Elige el método de pago.";
    static final String METHOD_UNAVAILABLE = "Ese método de pago ya no está disponible.";
    static final String CASH_REQUIRED = "Escribe cuánto efectivo recibiste.";
    static final String CASH_SHORT = "El efectivo recibido no cubre el total.";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Las fuentes de datos que necesita el borrador; las arma cada caso de uso. */
    record Sources(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
                   PaymentMethodRepository methods, SettingsRepository settings) {
        Sources {
            Objects.requireNonNull(members, "members");
            Objects.requireNonNull(memberships, "memberships");
            Objects.requireNonNull(plans, "plans");
            Objects.requireNonNull(methods, "methods");
            Objects.requireNonNull(settings, "settings");
        }
    }

    static ChargeDraft evaluate(ChargeMembershipCommand command, LocalDate today, Sources sources) {
        Map<String, String> errors = new LinkedHashMap<>();
        GymSettings settings = sources.settings().get();

        Member member = null;
        boolean memberMissing = false;
        if (command.memberId() == null) {
            errors.put("memberId", MEMBER_REQUIRED);
        } else {
            member = sources.members().findById(command.memberId()).orElse(null);
            if (member == null) {
                memberMissing = true;
                errors.put("memberId", MEMBER_NOT_FOUND);
            }
        }

        Plan plan = null;
        if (command.planId() == null) {
            errors.put("planId", PLAN_REQUIRED);
        } else {
            plan = sources.plans().findById(command.planId()).filter(Plan::active).orElse(null);
            if (plan == null) {
                errors.put("planId", PLAN_INACTIVE);
            }
        }

        PaymentMethod method = null;
        if (command.paymentMethodId() == null) {
            errors.put("paymentMethodId", METHOD_REQUIRED);
        } else {
            method = sources.methods().findById(command.paymentMethodId()).filter(PaymentMethod::active).orElse(null);
            if (method == null) {
                errors.put("paymentMethodId", METHOD_UNAVAILABLE);
            }
        }

        Money total = plan == null ? Money.ZERO : plan.price();
        Money cashReceived = null;
        Money change = null;
        Money missing = null;
        if (method != null && method.cash()) {
            cashReceived = command.cashReceived();
            if (cashReceived == null) {
                errors.put("cashReceived", CASH_REQUIRED);
            } else if (plan != null && cashReceived.compareTo(total) < 0) {
                errors.put("cashReceived", CASH_SHORT);
                missing = total.minus(cashReceived);
            } else if (plan != null) {
                change = cashReceived.minus(total);
            }
        }

        MemberRow memberRow = null;
        ChargeKind kind = null;
        LocalDate start = null;
        LocalDate end = null;
        if (member != null) {
            List<Membership> history = sources.memberships().findByMember(member.id());
            Membership latest = MembershipRules.latest(history).orElse(null);
            String latestPlanName = latest == null ? null
                    : sources.plans().findById(latest.planId()).map(Plan::name).orElse(null);
            memberRow = MemberRows.of(member, latest, latestPlanName, today, settings.warningDays(),
                    settings.autoDeactivationDays());
            kind = ChargeKind.forStatus(memberRow.status());
            if (kind == ChargeKind.PRIMERA) {
                start = command.start() == null ? today : command.start();
                if (start.isBefore(member.joinDate())) {
                    errors.put("start", "El inicio no puede ser anterior a la fecha de alta ("
                            + member.joinDate().format(DAY) + ").");
                }
            } else {
                start = MembershipRules.renewalStart(latest, today);
            }
            if (plan != null) {
                end = start.plusDays(plan.durationDays());
            }
        }

        return new ChargeDraft(member, memberMissing, memberRow, plan, method, kind, start, end, total,
                cashReceived, change, missing, errors);
    }
}
