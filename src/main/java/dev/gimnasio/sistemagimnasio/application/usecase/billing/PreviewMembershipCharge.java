package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** El resumen antes de cobrar. No guarda nada y solo lanza por permiso: lo incompleto queda en {@code errors}. */
public final class PreviewMembershipCharge {
    private final ChargeDraft.Sources sources;
    private final PlanRepository plans;
    private final Authorizer authorizer;
    private final Clock clock;

    public PreviewMembershipCharge(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
                                   PaymentMethodRepository methods, SettingsRepository settings,
                                   Authorizer authorizer, Clock clock) {
        this.sources = new ChargeDraft.Sources(members, memberships, plans, methods, settings);
        this.plans = plans;
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ChargePreview execute(User actor, ChargeMembershipCommand command) {
        authorizer.require(actor, Permission.CHARGE_MEMBERSHIP);
        ChargeDraft draft = ChargeDraft.evaluate(command, LocalDate.now(clock), sources);
        return new ChargePreview(
                draft.memberRow(),
                draft.plan() == null ? null : PlanOptions.of(draft.plan(), plans),
                draft.start(),
                draft.end(),
                draft.kind(),
                draft.total(),
                draft.method() == null ? null : PaymentMethodItem.of(draft.method()),
                draft.cashReceived(),
                draft.change(),
                draft.missing(),
                draft.errors(),
                draft.errors().isEmpty());
    }
}
