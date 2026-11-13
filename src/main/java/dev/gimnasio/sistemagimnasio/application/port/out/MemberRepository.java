package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.member.Member;

import java.util.Optional;

/** Lo que necesita el cobro; la búsqueda y el alta de socios se agregan con su paso. */
public interface MemberRepository {
    Optional<Member> findById(long id);
}
