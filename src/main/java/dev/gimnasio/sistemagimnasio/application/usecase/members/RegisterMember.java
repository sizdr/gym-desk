package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Alta de socio. Queda sin membresía: la primera se registra al cobrar. Revisar el correo repetido y guardar van
 * en la misma transacción, para que dos estaciones no registren el mismo correo.
 */
public final class RegisterMember {
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final SettingsRepository settings;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;
    private final Clock clock;

    public RegisterMember(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
                          SettingsRepository settings, TransactionRunner transactions, Authorizer authorizer,
                          Clock clock) {
        this.members = Objects.requireNonNull(members, "members");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MemberRow execute(User actor, MemberCommand command) {
        return transactions.inTransaction(() -> {
            authorizer.require(actor, Permission.EDIT_MEMBERS);
            LocalDate today = LocalDate.now(clock);
            Member saved = members.insert(MemberRules.validMember(command, null, today, null, members));
            return MemberRules.row(saved, memberships, plans, settings, today);
        });
    }
}
