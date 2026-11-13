package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresMembershipRepository implements MembershipRepository {
    private final Jdbi jdbi;

    public PostgresMembershipRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    @Override
    public List<Membership> findByMember(long memberId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento
                        FROM membresia
                        WHERE socio_id = :socio
                        ORDER BY fecha_inicio, id
                        """)
                .bind("socio", memberId)
                .map(PostgresMembershipRepository::map)
                .list());
    }

    @Override
    public Optional<Membership> findByPayment(long paymentId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento
                        FROM membresia
                        WHERE pago_id = :pago
                        """)
                .bind("pago", paymentId)
                .map(PostgresMembershipRepository::map)
                .findOne());
    }

    @Override
    public Membership insert(Membership membership) {
        long id = jdbi.withHandle(handle -> handle.createUpdate("""
                        INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento)
                        VALUES (:socio, :plan, :pago, :inicio, :vencimiento)
                        """)
                .bind("socio", membership.memberId())
                .bind("plan", membership.planId())
                .bind("pago", membership.paymentId())
                .bind("inicio", membership.start())
                .bind("vencimiento", membership.end())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
        return new Membership(id, membership.memberId(), membership.planId(), membership.paymentId(),
                membership.start(), membership.end());
    }

    private static Membership map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new Membership(
                rs.getLong("id"),
                rs.getLong("socio_id"),
                rs.getLong("plan_id"),
                rs.getLong("pago_id"),
                rs.getObject("fecha_inicio", LocalDate.class),
                rs.getObject("fecha_vencimiento", LocalDate.class));
    }
}
