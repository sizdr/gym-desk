package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Solo se borra un socio sin cobros (por ejemplo, un alta duplicada); uno con cobros conserva su historial. */
public final class DeleteMember {
    private final MemberRepository members;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public DeleteMember(MemberRepository members, TransactionRunner transactions, Authorizer authorizer) {
        this.members = Objects.requireNonNull(members, "members");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public void execute(User actor, long memberId) {
        transactions.run(() -> {
            authorizer.require(actor, Permission.EDIT_MEMBERS);
            Member member = MemberRules.find(members, memberId);
            MemberRules.requireNoPayments(members, member);
            members.delete(memberId);
        });
    }
}
