package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Email;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresMemberRepository implements MemberRepository {
    private final Jdbi jdbi;

    public PostgresMemberRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    private static final String COLUMNS = "SELECT id, nombre, telefono, correo, fecha_alta FROM socio";

    @Override
    public Optional<Member> findById(long id) {
        return jdbi.withHandle(handle -> handle.createQuery(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map(PostgresMemberRepository::map)
                .findOne());
    }

    @Override
    public List<Member> findAll() {
        return jdbi.withHandle(handle -> handle.createQuery(COLUMNS).map(PostgresMemberRepository::map).list());
    }

    @Override
    public Optional<String> nameWithEmail(String email, Long excludeId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT nombre
                        FROM socio
                        WHERE LOWER(correo) = LOWER(:correo)
                          AND (CAST(:excludeId AS BIGINT) IS NULL OR id <> CAST(:excludeId AS BIGINT))
                        """)
                .bind("correo", email)
                .bind("excludeId", excludeId)
                .mapTo(String.class)
                .findFirst());
    }

    @Override
    public Member insert(Member member) {
        long id = jdbi.withHandle(handle -> handle.createUpdate("""
                        INSERT INTO socio (nombre, telefono, correo, fecha_alta)
                        VALUES (:nombre, :telefono, :correo, :alta)
                        """)
                .bind("nombre", member.fullName())
                .bind("telefono", member.phone().value())
                .bind("correo", member.email() == null ? null : member.email().value())
                .bind("alta", member.joinDate())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
        return new Member(id, member.fullName(), member.phone(), member.email(), member.joinDate());
    }

    @Override
    public void update(Member member) {
        int updated = jdbi.withHandle(handle -> handle.createUpdate("""
                        UPDATE socio
                        SET nombre     = :nombre,
                            telefono   = :telefono,
                            correo     = :correo,
                            fecha_alta = :alta
                        WHERE id = :id
                        """)
                .bind("id", member.id())
                .bind("nombre", member.fullName())
                .bind("telefono", member.phone().value())
                .bind("correo", member.email() == null ? null : member.email().value())
                .bind("alta", member.joinDate())
                .execute());
        if (updated != 1) {
            throw new IllegalStateException("No existe el socio " + member.id() + ".");
        }
    }

    @Override
    public boolean hasPayments(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT EXISTS (SELECT 1 FROM pago WHERE socio_id = :id)")
                .bind("id", id)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public void delete(long id) {
        jdbi.useHandle(handle -> handle.createUpdate("DELETE FROM socio WHERE id = :id").bind("id", id).execute());
    }

    private static Member map(java.sql.ResultSet rs, org.jdbi.v3.core.statement.StatementContext ctx)
            throws java.sql.SQLException {
        String email = rs.getString("correo");
        return new Member(
                rs.getLong("id"),
                rs.getString("nombre"),
                new Phone(rs.getString("telefono").strip()),
                email == null ? null : new Email(email),
                rs.getObject("fecha_alta", LocalDate.class));
    }
}
