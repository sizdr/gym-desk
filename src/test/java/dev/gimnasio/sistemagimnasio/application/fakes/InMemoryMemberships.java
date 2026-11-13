package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class InMemoryMemberships implements MembershipRepository {
    private final List<Membership> all = new ArrayList<>();
    private long nextId = 1;
    public final TransactionGuard guard = new TransactionGuard();

    /** Una membresía anterior, con un pago ficticio. */
    public Membership add(long memberId, long planId, LocalDate start, LocalDate end) {
        Membership membership = new Membership(nextId, memberId, planId, 1000 + nextId, start, end);
        nextId++;
        all.add(membership);
        return membership;
    }

    @Override
    public List<Membership> findByMember(long memberId) {
        guard.check("findByMember");
        return all.stream()
                .filter(m -> m.memberId() == memberId)
                .sorted(Comparator.comparing(Membership::start).thenComparing(Membership::id))
                .toList();
    }

    @Override
    public Optional<Membership> findByPayment(long paymentId) {
        guard.check("findByPayment");
        return all.stream().filter(m -> m.paymentId() == paymentId).findFirst();
    }

    @Override
    public Membership insert(Membership membership) {
        guard.check("insert");
        Membership saved = new Membership(nextId++, membership.memberId(), membership.planId(), membership.paymentId(),
                membership.start(), membership.end());
        all.add(saved);
        return saved;
    }

    public List<Membership> all() {
        return List.copyOf(all);
    }
}
