package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Corrige los datos personales. La membresía no cambia aquí: solo con un cobro. */
public final class UpdateMember {
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final PlanRepository plans;
    private final SettingsRepository settings;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;
    private final Clock clock;

    public UpdateMember(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
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

    public MemberRow execute(User actor, long memberId, MemberCommand command) {
        return transactions.inTransaction(() -> {
            authorizer.require(actor, Permission.EDIT_MEMBERS);
            MemberRules.find(members, memberId);
            LocalDate today = LocalDate.now(clock);
            // La fecha de alta no puede quedar después de su primera membresía.
            LocalDate firstStart = memberships.findByMember(memberId).stream()
                    .map(Membership::start).min(LocalDate::compareTo).orElse(null);
            Member updated = MemberRules.validMember(command, memberId, today, firstStart, members);
            members.update(updated);
            return MemberRules.row(updated, memberships, plans, settings, today);
        });
    }
}
