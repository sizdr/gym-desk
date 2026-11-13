package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.member.Membership;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {
    /** Del inicio más antiguo al más reciente. */
    List<Membership> findByMember(long memberId);

    /** La membresía que generó ese cobro; cada cobro genera una sola. */
    Optional<Membership> findByPayment(long paymentId);

    /** @return la misma membresía con el id asignado */
    Membership insert(Membership membership);
}
