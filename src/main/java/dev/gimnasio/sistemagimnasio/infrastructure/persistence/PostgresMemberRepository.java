package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Email;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class PostgresMemberRepository implements MemberRepository {
    private final Jdbi jdbi;

    public PostgresMemberRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    @Override
    public Optional<Member> findById(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, nombre, telefono, correo, fecha_alta
                        FROM socio
                        WHERE id = :id
                        """)
                .bind("id", id)
                .map((rs, ctx) -> {
                    String email = rs.getString("correo");
                    return new Member(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            new Phone(rs.getString("telefono").strip()),
                            email == null ? null : new Email(email),
                            rs.getObject("fecha_alta", LocalDate.class));
                })
                .findOne());
    }
}
