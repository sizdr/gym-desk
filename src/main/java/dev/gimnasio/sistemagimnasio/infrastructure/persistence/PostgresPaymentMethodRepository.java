package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresPaymentMethodRepository implements PaymentMethodRepository {
    private final Jdbi jdbi;

    public PostgresPaymentMethodRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    @Override
    public List<PaymentMethod> findAll(boolean includeInactive) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, nombre, es_efectivo, activo
                        FROM metodo_pago
                        WHERE activo OR :includeInactive
                        ORDER BY LOWER(nombre), id
                        """)
                .bind("includeInactive", includeInactive)
                .map(PostgresPaymentMethodRepository::map)
                .list());
    }

    @Override
    public Optional<PaymentMethod> findById(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, nombre, es_efectivo, activo
                        FROM metodo_pago
                        WHERE id = :id
                        """)
                .bind("id", id)
                .map(PostgresPaymentMethodRepository::map)
                .findOne());
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM metodo_pago
                            WHERE LOWER(nombre) = LOWER(:nombre)
                              AND (CAST(:excludeId AS BIGINT) IS NULL OR id <> CAST(:excludeId AS BIGINT))
                        )
                        """)
                .bind("nombre", name)
                .bind("excludeId", excludeId)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public int countActive() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT COUNT(*)
                        FROM metodo_pago
                        WHERE activo
                        """)
                .mapTo(Integer.class)
                .one());
    }

    @Override
    public PaymentMethod insert(PaymentMethod method) {
        long id = jdbi.withHandle(handle -> handle.createUpdate("""
                        INSERT INTO metodo_pago (nombre, es_efectivo, activo)
                        VALUES (:nombre, :efectivo, :activo)
                        """)
                .bind("nombre", method.name())
                .bind("efectivo", method.cash())
                .bind("activo", method.active())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
        return new PaymentMethod(id, method.name(), method.cash(), method.active());
    }

    @Override
    public void update(PaymentMethod method) {
        int updated = jdbi.withHandle(handle -> handle.createUpdate("""
                        UPDATE metodo_pago
                        SET nombre      = :nombre,
                            es_efectivo = :efectivo,
                            activo      = :activo
                        WHERE id = :id
                        """)
                .bind("id", method.id())
                .bind("nombre", method.name())
                .bind("efectivo", method.cash())
                .bind("activo", method.active())
                .execute());
        if (updated != 1) {
            throw new IllegalStateException("No existe el método de pago " + method.id() + ".");
        }
    }

    @Override
    public boolean isUsed(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT EXISTS (SELECT 1 FROM pago WHERE metodo_pago_id = :id)
                        """)
                .bind("id", id)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public void delete(long id) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                        DELETE FROM metodo_pago
                        WHERE id = :id
                        """)
                .bind("id", id)
                .execute());
    }

    private static PaymentMethod map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new PaymentMethod(
                rs.getLong("id"),
                rs.getString("nombre"),
                rs.getBoolean("es_efectivo"),
                rs.getBoolean("activo"));
    }
}
