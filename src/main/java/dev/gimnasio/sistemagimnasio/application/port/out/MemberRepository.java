package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.member.Member;

import java.util.List;
import java.util.Optional;

/** Los socios: su catálogo (alta, consulta, edición y borrado) y lo que necesita el cobro. */
public interface MemberRepository {
    Optional<Member> findById(long id);

    /** Todos, sin orden garantizado: el caso de uso filtra y ordena con el estado, que no se guarda. */
    List<Member> findAll();

    /** El nombre del socio que ya usa ese correo (sin distinguir mayúsculas); {@code excludeId} no cuenta. */
    Optional<String> nameWithEmail(String email, Long excludeId);

    /** @return el mismo socio con el id asignado */
    Member insert(Member member);

    /** Nombre, teléfono, correo y fecha de alta. Lanza {@code IllegalStateException} si el id no existe. */
    void update(Member member);

    /** {@code true} si ya tiene cobros: entonces no se puede borrar. */
    boolean hasPayments(long id);

    void delete(long id);
}
